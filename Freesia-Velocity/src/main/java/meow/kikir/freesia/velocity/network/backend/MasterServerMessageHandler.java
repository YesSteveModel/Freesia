package meow.kikir.freesia.velocity.network.backend;

import com.google.common.collect.Maps;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.handler.ServerChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.m2w.M2WDispatchCommandMessage;
import meow.kikir.freesia.common.data.WorkerInfo;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.events.PlayerEntityDataStoreEvent;
import meow.kikir.freesia.velocity.events.WorkerConnectedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

public class MasterServerMessageHandler extends ServerChannelHandlerBase {
    private final Map<Integer, Consumer<String>> pendingCommandDispatches = Maps.newConcurrentMap();
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);

    private volatile boolean commandDispatcherRetired = false;
    private final StampedLock commandDispatchCallbackLock = new StampedLock();

    public MasterServerMessageHandler(Channel channel) {
        super(channel);
    }

    public void dispatchCommandToWorker(String command, Consumer<Component> onDispatched) {
        final long stamp = this.commandDispatchCallbackLock.readLock();
        try {
            // We were retired during connection
            if (this.commandDispatcherRetired) {
                onDispatched.accept(null);
                return;
            }

            final int traceId = this.traceIdGenerator.getAndIncrement();

            final Consumer<String> wrappedDecoder = json -> {
                try {
                    // We were retired during disconnection
                    if (json == null) {
                        onDispatched.accept(null);
                        return;
                    }

                    final Component decoded = LegacyComponentSerializer.builder().build().deserialize(json);
                    onDispatched.accept(decoded);
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to decode command result from worker", e);
                    onDispatched.accept(null);
                }
            };

            this.pendingCommandDispatches.put(traceId, wrappedDecoder);
            this.sendMessage(new M2WDispatchCommandMessage(traceId, command));
        }finally {
            this.commandDispatchCallbackLock.unlockRead(stamp);
        }
    }

    @Nullable
    public UUID getWorkerUUID() {
        final WorkerInfo local = this.workerInfo;

        if (local == null) {
            return null;
        }

        return local.workerUUID();
    }

    @Nullable
    public String getWorkerName() {
        final WorkerInfo local = this.workerInfo;

        if (local == null) {
            return null;
        }

        return local.workerName();
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        this.retireAllCommandDispatchCallbacks();

        final WorkerInfo local = this.workerInfo;

        if (local == null) {
            return;
        }

        Freesia.registedWorkers.remove(local.workerUUID());
    }

    @Override
    public Map<Path, Path> collectModelFiles() {
        final Path baseDir = FreesiaConstants.FileConstants.PLUGIN_DIR.toPath().resolve("models");

        final Map<Path, Path> collected = new LinkedHashMap<>();
        for (String subFolders : FreesiaConstants.FileConstants.MODEL_FOLDERS_SUB) {
            final Path currBaseDir = baseDir.resolve(subFolders);
            final File currFile = currBaseDir.toFile();
            currFile.mkdirs();

            try {
                Files.walkFileTree(currBaseDir, new FileVisitor<>() {
                    @Override
                    public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attrs) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) {
                        final Path relativePath = FreesiaConstants.FileConstants.YSM_MODELS_BASE_DIR.resolve(baseDir.relativize(file));

                        if (Files.isDirectory(relativePath)) {
                            return FileVisitResult.CONTINUE;
                        }

                        collected.put(file, relativePath);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFileFailed(Path file, @NotNull IOException exc) {
                        EntryPoint.LOGGER_INST.warn("Failed to visit model file: {}", file, exc);
                        MasterServerMessageHandler.this.modelSyncFailed(exc);
                        return FileVisitResult.TERMINATE;
                    }

                    @Override
                    public @NotNull FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            }catch (Throwable th) {
                this.modelSyncFailed(th);
            }
        }

        return collected;
    }

    private void modelSyncFailed(Throwable throwable) {
        this.getChannel().disconnect();
        EntryPoint.LOGGER_INST.error("Failed to collect model files!", throwable);
        throw new RuntimeException(throwable);
    }

    private void retireAllCommandDispatchCallbacks() {
        final long stamp = this.commandDispatchCallbackLock.writeLock();
        try {
            this.commandDispatcherRetired = true;

            for (Map.Entry<Integer, Consumer<String>> entry : this.pendingCommandDispatches.entrySet()) {
                entry.getValue().accept(null);
            }

            this.pendingCommandDispatches.clear();
        }finally {
            this.commandDispatchCallbackLock.unlockWrite(stamp);
        }
    }

    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content) {
        final CompletableFuture<Void> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER
                .getEventManager()
                .fire(new PlayerEntityDataStoreEvent(playerUUID, content))
                .thenAccept(event -> Freesia.realPlayerDataStorageManager.save(playerUUID, event.getSerializedNbtData())
                        .whenComplete((res, ex) -> {
                            if (ex != null) {
                                callback.completeExceptionally(ex);
                                return;
                            }

                            callback.complete(res);
                        }));

        return callback;
    }

    @Override
    public void onCommandDispatchResult(int traceId, @Nullable String result) {
        final Consumer<String> removedDecoder = this.pendingCommandDispatches.remove(traceId);

        if (removedDecoder != null) {
            removedDecoder.accept(result);
        }
    }

    @Override
    public void onWorkerInfoGet(WorkerInfo workerInfo) {
        Freesia.registedWorkers.put(workerInfo.workerUUID(), this);

        Freesia.PROXY_SERVER.getEventManager().fire(new WorkerConnectedEvent(workerInfo.workerUUID(), workerInfo.workerName()));
    }
}
