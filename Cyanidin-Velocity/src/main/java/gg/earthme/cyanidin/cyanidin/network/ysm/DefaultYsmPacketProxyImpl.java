package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.velocitypowered.api.proxy.Player;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.network.mc.NbtRemapper;
import gg.earthme.cyanidin.cyanidin.network.mc.impl.StandardNbtRemapperImpl;
import gg.earthme.cyanidin.cyanidin.utils.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.locks.LockSupport;

public class DefaultYsmPacketProxyImpl implements YsmPacketProxy{
    private final Player player;
    private final int masterProtocolVer;
    private final NbtRemapper nbtRemapper;

    public DefaultYsmPacketProxyImpl(@NotNull Player player) {
        this.player = player;
        this.masterProtocolVer = PacketEvents.getAPI().getProtocolManager().getClientVersion(PacketEvents.getAPI().getProtocolManager().getChannel(player.getUniqueId())).getProtocolVersion();
        this.nbtRemapper = new StandardNbtRemapperImpl();
    }

    @Override
    public void blockUntilProxyReady(){
        while (Cyanidin.mapperManager.getPlayerEntityId(this.player) == -1){
            Thread.yield();
            LockSupport.parkNanos(1_000);
        }

        Cyanidin.mapperManager.onPacketProxyReady(this.player);
    }

    @Override
    public EnumPacketProxyResult processS2C(Key key, ByteBuf copiedPacketData, ByteBuf direct) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final FriendlyByteBuf directMCBuffer = new FriendlyByteBuf(direct);

        directMCBuffer.writeBytes(mcBuffer);

        final byte packetId = directMCBuffer.readByte();

        switch (packetId){
            case 51 -> {
                final String backendVersion = directMCBuffer.readUtf();
                final boolean canSwitchModel = directMCBuffer.readBoolean();
                Cyanidin.LOGGER.info("Replying ysm client with server version {}.Can switch model? : {}", backendVersion, canSwitchModel);
                return EnumPacketProxyResult.FORWARD;
            }

            case 4 -> {
                directMCBuffer.clear();
                directMCBuffer.writeByte(4);
                final int originalEntityId = mcBuffer.readVarInt();
                final int entityId = Cyanidin.mapperManager.getPlayerEntityId(this.player);

                Cyanidin.LOGGER.info("Tracker status changed for entityId {} to {} as mapped on backends", originalEntityId, entityId);
                directMCBuffer.writeVarInt(entityId);

                try {
                    final NBTCompound original = this.nbtRemapper.readBound(mcBuffer);

                    byte[] remapped;
                    if (this.nbtRemapper.shouldRemap(this.masterProtocolVer)){ //If target's protocol version is lower than 1.20.1
                        remapped = this.nbtRemapper.remapToMasterVer(original);
                    }else{
                        remapped = this.nbtRemapper.remapToWorkerVer(original);
                    }
                    directMCBuffer.writeBytes(remapped);

                    final Set<Player> beingWatched = Cyanidin.tracker.getCanSee(this.player);
                    for (Player target : beingWatched){
                        final int targetProtocolId = PacketEvents.getAPI().getProtocolManager().getClientVersion(PacketEvents.getAPI().getProtocolManager().getChannel(target.getUniqueId())).getProtocolVersion();

                        byte[] resultNbt; //1.20.1 format
                        if (this.nbtRemapper.shouldRemap(targetProtocolId)){ //If target's protocol version is lower than 1.20.1
                            resultNbt = this.nbtRemapper.remapToMasterVer(original);
                        }else {
                            resultNbt = this.nbtRemapper.remapToWorkerVer(original);
                        }

                        final FriendlyByteBuf subBuffer = new FriendlyByteBuf(Unpooled.directBuffer());
                        try {
                            subBuffer.writeByte(4); //Pkt id
                            subBuffer.writeVarInt(entityId); //Entity id
                            subBuffer.writeBytes(resultNbt);

                            byte[] fullPacketData = new byte[subBuffer.readableBytes()];
                            subBuffer.readBytes(fullPacketData);

                            target.sendPluginMessage(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, fullPacketData); //Send
                        }finally {
                            subBuffer.release();
                        }
                    }
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Error while in processing nbt data!", e);
                    return EnumPacketProxyResult.DROP;
                }
                return EnumPacketProxyResult.FORWARD;
            }
        }
        return EnumPacketProxyResult.FORWARD;
    }

    @Override
    public EnumPacketProxyResult processC2S(Key key, ByteBuf copiedPacketData, ByteBuf direct) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final FriendlyByteBuf directMCBuffer = new FriendlyByteBuf(direct);

        final byte packetId = mcBuffer.readByte();

        switch (packetId){
            case  52 -> {
                directMCBuffer.writeBytes(mcBuffer); //Copy original data because we need to forward it

                final String clientYsmVersion = mcBuffer.readUtf();
                Cyanidin.LOGGER.info("Player {} is connection to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
                Cyanidin.mapperManager.onClientYsmPacketReply(this.player);
                return EnumPacketProxyResult.FORWARD;
            }
        }
        return EnumPacketProxyResult.FORWARD;
    }
}
