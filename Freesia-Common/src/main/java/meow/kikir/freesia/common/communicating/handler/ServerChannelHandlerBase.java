package meow.kikir.freesia.common.communicating.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.file.FileChunk;
import meow.kikir.freesia.common.communicating.file.FileChunks;
import meow.kikir.freesia.common.communicating.message.IMessage;
import meow.kikir.freesia.common.communicating.message.m2w.M2WFileTransformationPacket;
import meow.kikir.freesia.common.communicating.message.m2w.M2WReadyPacket;
import meow.kikir.freesia.common.communicating.message.m2w.M2WReloadModelsCallCommand;
import meow.kikir.freesia.common.data.WorkerInfo;
import meow.kikir.freesia.common.utils.LinkedObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ServerChannelHandlerBase extends SimpleChannelInboundHandler<IMessage<ServerChannelHandlerBase>> {
    private static final int FILE_CHUNK_SIZE = 1024 * 128; // 128 KB

    // file transformations
    private final Map<Integer, FileChunks> fileTransferringRequests = new ConcurrentHashMap<>();
    private final Map<Integer, Runnable> fileTransferCallbacks = new ConcurrentHashMap<>();
    private final AtomicInteger fileTransferTraceIdGenerator = new AtomicInteger(0);

    protected volatile WorkerInfo workerInfo;
    private final Channel channel;

    protected ServerChannelHandlerBase(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        EntryPoint.LOGGER_INST.info("Worker connected {}", this.channel);

        this.doSyncModels(false);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        this.dropAllTransferringFiles();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<ServerChannelHandlerBase> msg) {
        try {
            msg.process(this);
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(IMessage<ClientChannelHandlerBase> packet) {
        if (!this.channel.isOpen()) {
            return;
        }

        if (!this.channel.eventLoop().inEventLoop()) {
            this.channel.eventLoop().execute(() -> this.sendMessage(packet));
            return;
        }

        this.channel.writeAndFlush(packet);
    }

    public abstract CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content);

    public abstract void onCommandDispatchResult(int traceId, @Nullable String result);

    public void updateWorkerInfo(UUID workerUUID, String workerName) {
        this.workerInfo = new WorkerInfo(workerUUID, workerName);

        this.onWorkerInfoGet(this.workerInfo);

        EntryPoint.LOGGER_INST.info("Worker {} (UUID: {}) connected", workerName, workerUUID);
    }

    public abstract void onWorkerInfoGet(WorkerInfo workerInfo);

    private void dropAllTransferringFiles() {
        for (Map.Entry<Integer, FileChunks> entry : this.fileTransferringRequests.entrySet()) {
            try {
                entry.getValue().channel().close();
            }catch (Exception e) {
                EntryPoint.LOGGER_INST.error("Failed to close file channel for trace id {}, file: {}", entry.getKey(), entry.getValue(), e);
            }
        }
        this.fileTransferringRequests.clear();
    }

    public abstract Map<Path, Path> collectModelFiles();

    // used for api
    public void callWorkerModelReload() {
        this.doSyncModels(true);
    }

    public void doSyncModels(boolean syncForReload) {
        final Map<Path, Path> modelFiles = this.collectModelFiles();

        final AtomicInteger latch = new AtomicInteger();
        final CompletableFuture<Void> callback = new CompletableFuture<>();

        if (!modelFiles.isEmpty()) {
            for (Map.Entry<Path, Path> modelPathEntry : modelFiles.entrySet()) {
                final Path readerPath = modelPathEntry.getKey();
                final Path outerPath = modelPathEntry.getValue();

                EntryPoint.LOGGER_INST.info("Sending model file: {}.", readerPath);
                try {
                    latch.getAndIncrement();
                    this.sendFile(readerPath, outerPath, () -> {
                        int curr = latch.decrementAndGet();
                        if (curr == 0) {
                            callback.complete(null);
                        }

                        EntryPoint.LOGGER_INST.info("Finished syncing model file: {}.Current callback queue size: {}", readerPath, this.fileTransferCallbacks.size());
                    });

                }catch (IOException e) {
                    EntryPoint.LOGGER_INST.error("Failed to sync model file: {}", readerPath, e);
                }
            }

            // we might send no files if all are empty
            int curr = latch.get();
            if (curr == 0) {
                callback.complete(null);
            }
        }else {
            callback.complete(null);
        }

        callback.whenComplete((_, ex) -> {
            if (ex != null) {
                // synchronization failed, throw exception and disconnect as we had a broken models folder
                this.modelSynchronizedFailed(ex);
                return;
            }

            EntryPoint.LOGGER_INST.info("Model files synchronization completed. Waking up worker.");

            if (!syncForReload) {
                // this would only happen on the connection is firstly established
                // as we need to force block the worker to wait all models are completely synchronized
                this.sendMessage(new M2WReadyPacket());
                return;
            }

            // otherwise we could just do it asynchronously
            this.sendMessage(new M2WReloadModelsCallCommand());
        });
    }

    public void modelSynchronizedFailed(Throwable ex) {
        EntryPoint.LOGGER_INST.error("Failed to synchronize model files! Forcing to disconnect and reconnect!", ex);

        this.channel.disconnect();
        this.dropAllTransferringFiles();
    }

    public void sendFile(Path readerPath, @NotNull Path outerPath, Runnable callback) throws IOException {
        final int traceId = this.fileTransferTraceIdGenerator.getAndIncrement();
        final long fileLength = Files.size(readerPath);

        // give file not found
        if (fileLength == 0) {
            EntryPoint.LOGGER_INST.info("Skipping non-existing or empty file: {}", readerPath);
            callback.run();
            return;
        }

        final int totalChunks = fileLength % FILE_CHUNK_SIZE == 0 ?
                (int)(fileLength / FILE_CHUNK_SIZE) :
                (int)(fileLength / FILE_CHUNK_SIZE + 1);
        final LinkedObjects<FileChunk> chunks = new LinkedObjects<>();
        final FileChannel targetChannel = FileChannel.open(readerPath, StandardOpenOption.READ);
        try {
            final FileChunks fileChunks = new FileChunks(totalChunks, targetChannel, chunks);

            int id = 0;
            for (int i = 0; i < totalChunks; i++) {
                final int offset = i * FILE_CHUNK_SIZE;
                final int chunkSize = (int)Math.min(FILE_CHUNK_SIZE, fileLength - offset);

                final FileChunk createdChunk = new FileChunk(outerPath, chunk -> {
                    try {
                        byte[] data = new byte[chunkSize];
                        targetChannel.read(ByteBuffer.wrap(data), chunk.offset());
                        return data;
                    }catch (Exception e){
                        EntryPoint.LOGGER_INST.error("Failed to read file chunk: {}", readerPath, e);

                        try {
                            targetChannel.close();
                        } catch (IOException ex2) {
                            EntryPoint.LOGGER_INST.error("Failed to close file channel after read failure: {}", readerPath, ex2);
                            e.addSuppressed(ex2);
                        }

                        throw new RuntimeException(e);
                    }
                }, offset, chunkSize);
                chunks.setValue(id, createdChunk);
                id++;
            }

            this.fileTransferringRequests.put(traceId, fileChunks);
            this.fileTransferCallbacks.put(traceId, callback);

            // send the first chunk
            final FileChunk firstChunk = chunks.getValue();
            final byte[] fileData = firstChunk.reader().apply(firstChunk);
            final int beginOffset = firstChunk.offset();

            this.sendMessage(new M2WFileTransformationPacket(
                    traceId,
                    0,
                    totalChunks,
                    beginOffset,
                    firstChunk.pathRelativity(),
                    fileData
            ));
        }catch (Exception e) {
            try {
                targetChannel.close();
            }catch (Exception e2){
                e.addSuppressed(e2);
            }

            throw new IOException(e);
        }

    }

    public void handleFileAck(int traceId, int ack) {
        final FileChunks fileChunks = this.fileTransferringRequests.get(traceId);
        if (fileChunks == null) {
            EntryPoint.LOGGER_INST.warn("Unknown file transfer ack received: traceId={}", traceId);
            return;
        }

        final LinkedObjects<FileChunk> fileChunk = fileChunks.chunks();
        final Path target = fileChunk.getValue().pathRelativity();
        final int totalEntries = fileChunks.totalChunks();

        if (ack != fileChunk.getCurrentId()) {
            this.fileTransferringRequests.remove(traceId);

            try {
                fileChunks.channel().close();
            }catch (Exception e) {
                EntryPoint.LOGGER_INST.error("Failed to close file channel for trace id {}, file: {}", traceId, fileChunks, e);
            }

            throw new IllegalStateException("Mis-ordered file transfer ack received, traceId=" + traceId + ", ack=" + ack + ", expected=" + fileChunk.getCurrentId());
        }

        final FileChunk next = fileChunk.next();
        if (next == null) {
            // all has done.
            // send tAck = -1
            this.sendMessage(new M2WFileTransformationPacket(traceId, -1, -1, -1, target, null));
            this.fileTransferringRequests.remove(traceId);

            try {
                fileChunks.channel().close();
            }catch (Exception e) {
                EntryPoint.LOGGER_INST.error("Failed to close file channel for trace id {}, file: {}", traceId, fileChunks, e);
            }

            final Runnable callback = this.fileTransferCallbacks.remove(traceId);
            if (callback != null) {
                callback.run();
            }
            return;
        }

        // remove the header entry as we finished transferring
        fileChunk.upgradeToNext();

        final Path targetPath = next.pathRelativity();
        final byte[] fileData = next.reader().apply(next);
        final int beginOffset = next.offset();

        this.sendMessage(new M2WFileTransformationPacket(
                traceId,
                fileChunk.getCurrentId(),
                totalEntries,
                beginOffset,
                targetPath,
                fileData
        ));
    }

    public Channel getChannel() {
        return this.channel;
    }
}
