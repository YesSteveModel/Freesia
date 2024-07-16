package gg.earthme.cyanidin.cyanidinworker.impl;

import com.google.common.collect.Maps;
import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MPlayerDataGetRequestMessage;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MUpdatePlayerDataRequestMessage;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class WorkerMessageHandlerImpl extends NettyClientChannelHandlerLayer {
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);
    private final Map<Integer, Consumer<String>> playerDataGetCallbacks = Maps.newConcurrentMap();

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ServerLoader.workerConnection = this;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ServerLoader.SERVER_INST.execute(() -> {throw new RuntimeException("MASTER DISCONNECTED");}); //TODO Better handling?
    }

    public void getPlayerData(UUID playerUUID, Consumer<CompoundTag> onGot){
        final int generatedTraceId = this.traceIdGenerator.getAndIncrement();
        final Consumer<String> wrappedDecoder = nbtStr -> {
            CompoundTag decoded = null;

            if (nbtStr == null){
                onGot.accept(null);
                return;
            }

            try {
                final byte[] decodedNbtData = Base64.getDecoder().decode(nbtStr);
                decoded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(decodedNbtData)));
            }catch (Exception e){
                EntryPoint.LOGGER_INST.error("Failed to decode nbt!", e);
            }

            onGot.accept(decoded);
        };

        this.playerDataGetCallbacks.put(generatedTraceId, wrappedDecoder);

        ServerLoader.clientInstance.sendToMaster(new W2MPlayerDataGetRequestMessage(playerUUID, generatedTraceId));
    }

    @Override
    public void onMasterPlayerDataResponse(int traceId, String base64Content){
        final Consumer<String> removed = this.playerDataGetCallbacks.remove(traceId);

        if (removed == null){
            EntryPoint.LOGGER_INST.warn("Null traceId {} !", traceId);
            return;
        }

        try {
            removed.accept(base64Content);
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to fire player data callback!", e);
        }
    }

    public void updatePlayerData(UUID playerUUID, CompoundTag data){
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            NbtIo.write(data, dos);
            dos.flush();

            final byte[] content = bos.toByteArray();
            final String encoded = new String(Base64.getEncoder().encode(content), StandardCharsets.UTF_8);

            ServerLoader.clientInstance.sendToMaster(new W2MUpdatePlayerDataRequestMessage(playerUUID, encoded));
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to encode nbt!", e);
        }
    }
}
