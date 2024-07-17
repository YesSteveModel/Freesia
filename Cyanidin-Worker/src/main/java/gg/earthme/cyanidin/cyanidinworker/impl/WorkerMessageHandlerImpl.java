package gg.earthme.cyanidin.cyanidinworker.impl;

import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.NettySocketClient;
import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MPlayerDataGetRequestMessage;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MUpdatePlayerDataRequestMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@ChannelHandler.Sharable
public class WorkerMessageHandlerImpl extends NettyClientChannelHandlerLayer {
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);
    private final Map<Integer, Consumer<byte[]>> playerDataGetCallbacks = Maps.newConcurrentMap();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ServerLoader.workerConnection = this;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        super.channelInactive(ctx);
        ServerLoader.SERVER_INST.execute(ServerLoader::connectToBackend);
    }

    public void getPlayerData(UUID playerUUID, Consumer<CompoundTag> onGot){
        final int generatedTraceId = this.traceIdGenerator.getAndIncrement();
        final Consumer<byte[]> wrappedDecoder = content -> {
            CompoundTag decoded = null;

            if (content == null){
                onGot.accept(null);
                return;
            }

            try {
                decoded = (CompoundTag) NbtIo.readAnyTag(new DataInputStream(new ByteArrayInputStream(content)), NbtAccounter.unlimitedHeap());
            }catch (Exception e){
                EntryPoint.LOGGER_INST.error("Failed to decode nbt!", e);
            }

            onGot.accept(decoded);
        };

        this.playerDataGetCallbacks.put(generatedTraceId, wrappedDecoder);

        ServerLoader.clientInstance.sendToMaster(new W2MPlayerDataGetRequestMessage(playerUUID, generatedTraceId));
    }

    @Override
    public NettySocketClient getClient() {
        return ServerLoader.clientInstance;
    }

    @Override
    public void onMasterPlayerDataResponse(int traceId, byte[] content){
        final Consumer<byte[]> removed = this.playerDataGetCallbacks.remove(traceId);

        if (removed == null){
            EntryPoint.LOGGER_INST.warn("Null traceId {} !", traceId);
            return;
        }

        try {
            removed.accept(content);
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to fire player data callback!", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> callReloadModel() {
        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Runnable scheduledCommand = () -> {
            final String command = "ysm model reload";

            CommandDispatcher<CommandSourceStack> commandDispatcher = ServerLoader.SERVER_INST.getCommands().getDispatcher();
            final ParseResults<CommandSourceStack> parsed = commandDispatcher.parse(command, ServerLoader.SERVER_INST.createCommandSourceStack().withCallback((succeed, result) -> {
                callback.complete(succeed);
            }));

            ServerLoader.SERVER_INST.getCommands().performCommand(parsed, command);
        };
        ServerLoader.SERVER_INST.execute(scheduledCommand);

        return callback;
    }

    public void updatePlayerData(UUID playerUUID, CompoundTag data){
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            NbtIo.writeAnyTag(data, dos);
            dos.flush();

            final byte[] content = bos.toByteArray();

            ServerLoader.clientInstance.sendToMaster(new W2MUpdatePlayerDataRequestMessage(playerUUID, content));
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to encode nbt!", e);
        }
    }
}
