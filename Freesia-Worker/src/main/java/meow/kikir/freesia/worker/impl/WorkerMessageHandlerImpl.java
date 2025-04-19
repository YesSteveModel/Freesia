package meow.kikir.freesia.worker.impl;

import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketClient;
import meow.kikir.freesia.common.communicating.handler.NettyClientChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.w2m.W2MPlayerDataGetRequestMessage;
import meow.kikir.freesia.common.communicating.message.w2m.W2MUpdatePlayerDataRequestMessage;
import meow.kikir.freesia.common.communicating.message.w2m.W2MWorkerInfoMessage;
import meow.kikir.freesia.worker.ServerLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

public class WorkerMessageHandlerImpl extends NettyClientChannelHandlerLayer {
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);
    private final Map<Integer, Consumer<byte[]>> playerDataGetCallbacks = Maps.newConcurrentMap();

    private volatile boolean playerDataFetchCallbackRetired = false;
    private final StampedLock playerDataFetchCallbackLock = new StampedLock();

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        super.channelActive(ctx);

        this.getClient().sendToMaster(new W2MWorkerInfoMessage(ServerLoader.workerInfoFile.workerUUID(), ServerLoader.workerInfoFile.workerName()));

        ServerLoader.workerConnection = this;
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        this.retirePlayerFetchCallbacks();
        super.channelInactive(ctx);
        ServerLoader.SERVER_INST.execute(ServerLoader::connectToBackend);
    }

    private void retirePlayerFetchCallbacks() {
        final long stamp = this.playerDataFetchCallbackLock.writeLock();
        try {
            this.playerDataFetchCallbackRetired = true;

            for (Map.Entry<Integer, Consumer<byte[]>> entry : this.playerDataGetCallbacks.entrySet()) {
                try {
                    entry.getValue().accept(null);
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to fire player data callback!", e);
                }
            }

            this.playerDataGetCallbacks.clear();
        }finally {
            this.playerDataFetchCallbackLock.unlockWrite(stamp);
        }
    }

    public void getPlayerData(UUID playerUUID, Consumer<CompoundTag> onGot) {
        final long stamp = this.playerDataFetchCallbackLock.readLock();
        try {
            if (this.playerDataFetchCallbackRetired) {
                onGot.accept(null);
                return;
            }

            final int generatedTraceId = this.traceIdGenerator.getAndIncrement();

            final Consumer<byte[]> wrappedDecoder = content -> {
                CompoundTag decoded = null;

                if (content == null) {
                    onGot.accept(null);
                    return;
                }

                try {
                    decoded = (CompoundTag) NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(content)), NbtAccounter.unlimitedHeap());
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to decode nbt!", e);
                }

                onGot.accept(decoded);
            };

            this.playerDataGetCallbacks.put(generatedTraceId, wrappedDecoder);

            ServerLoader.clientInstance.sendToMaster(new W2MPlayerDataGetRequestMessage(playerUUID, generatedTraceId));
        }finally {
            this.playerDataFetchCallbackLock.unlockRead(stamp);
        }
    }

    @Override
    public NettySocketClient getClient() {
        return ServerLoader.clientInstance;
    }

    @Override
    public void onMasterPlayerDataResponse(int traceId, byte[] content) {
        final Consumer<byte[]> removed = this.playerDataGetCallbacks.remove(traceId);

        if (removed == null) {
            EntryPoint.LOGGER_INST.warn("Null traceId {} !", traceId);
            return;
        }

        try {
            removed.accept(content);
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to fire player data callback!", e);
        }
    }

    @Override
    public CompletableFuture<String> dispatchCommand(String command) {
        final CompletableFuture<String> callback = new CompletableFuture<>();

        Runnable scheduledCommand = () -> {
            CommandDispatcher<CommandSourceStack> commandDispatcher = ServerLoader.SERVER_INST.getCommands().getDispatcher();

            final ParseResults<CommandSourceStack> parsed = commandDispatcher.parse(command, ServerLoader.SERVER_INST.createCommandSourceStack().withSource(new CommandSource() {
                @Override
                public void sendSystemMessage(Component component) {
                    callback.complete(component.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            }));

            ServerLoader.SERVER_INST.getCommands().performCommand(parsed, command);
        };
        ServerLoader.SERVER_INST.execute(scheduledCommand);

        return callback;
    }

    public void updatePlayerData(UUID playerUUID, CompoundTag data) {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            NbtIo.writeAnyTag(data, dos);
            dos.flush();

            final byte[] content = bos.toByteArray();

            ServerLoader.clientInstance.sendToMaster(new W2MUpdatePlayerDataRequestMessage(playerUUID, content));
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to encode nbt!", e);
        }
    }
}
