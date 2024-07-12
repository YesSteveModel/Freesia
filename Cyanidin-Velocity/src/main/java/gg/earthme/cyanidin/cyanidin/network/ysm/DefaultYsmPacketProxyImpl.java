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
    private final NbtRemapper nbtRemapper;
    private volatile NBTCompound lastYsmEntityStatus = null; //TODO Get default

    public DefaultYsmPacketProxyImpl(@NotNull Player player) {
        this.player = player;
        this.nbtRemapper = new StandardNbtRemapperImpl();
    }

    @Override
    public void blockUntilProxyReady(){
        while (Cyanidin.mapperManager.getServerPlayerEntityId(this.player) == -1){
            Thread.yield();
            LockSupport.parkNanos(1_000);
        }

        Cyanidin.mapperManager.onPacketProxyReady(this.player);
    }

    @Override
    public Player getOwner() {
        return this.player;
    }

    private boolean isEntityStateOfSelf(int entityId){
        final int currentWorkerEntityId = Cyanidin.mapperManager.getWorkerPlayerEntityId(this.player);

        if (currentWorkerEntityId == -1){
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    public void sendEntityStateTo(@NotNull Player target){
        final int currentEntityId = Cyanidin.mapperManager.getServerPlayerEntityId(this.player);

        final NBTCompound lastEntityStatusTemp = this.lastYsmEntityStatus;

        if (lastEntityStatusTemp == null || currentEntityId == -1){
            return;
        }

        try {
            final int targetProtocolVer = PacketEvents.getAPI().getProtocolManager().getClientVersion(PacketEvents.getAPI().getProtocolManager().getChannel(target.getUniqueId())).getProtocolVersion();
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(currentEntityId);
            wrappedPacketData.writeBytes(this.nbtRemapper.shouldRemap(targetProtocolVer) ? this.nbtRemapper.remapToMasterVer(lastEntityStatusTemp) : this.nbtRemapper.remapToWorkerVer(lastEntityStatusTemp));

            this.sendPluginMessageTo(target, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        }catch (Exception e){
            Cyanidin.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        switch (packetId){
            case 51 -> {
                final String backendVersion = mcBuffer.readUtf();
                final boolean canSwitchModel = mcBuffer.readBoolean();
                Cyanidin.LOGGER.info("Replying ysm client with server version {}.Can switch model? : {}", backendVersion, canSwitchModel);
                return ProxyComputeResult.ofPass();
            }

            case 4 -> {
                final int workerEntityId = mcBuffer.readVarInt();

                try {
                    if (!this.isEntityStateOfSelf(workerEntityId)){
                        return ProxyComputeResult.ofDrop(); //Do not process the entity state if it is not ours
                    }

                    final NBTCompound original = this.nbtRemapper.readBound(mcBuffer);

                    this.lastYsmEntityStatus = original;

                    this.sendEntityStateTo(this.player);

                    final Set<Player> beingWatched = Cyanidin.tracker.getCanSee(this.player);
                    for (Player target : beingWatched){
                        if (!Cyanidin.mapperManager.isPlayerInstalledYsm(target)){
                            continue;
                        }

                        this.sendEntityStateTo(target);
                    }
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Error while in processing nbt data!", e);
                    return ProxyComputeResult.ofDrop();
                }

                return ProxyComputeResult.ofDrop();
            }
        }

        return ProxyComputeResult.ofPass();
    }

    @Override
    public ProxyComputeResult processC2S(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        switch (packetId){
            case  52 -> {
                final String clientYsmVersion = mcBuffer.readUtf();
                Cyanidin.LOGGER.info("Player {} is connection to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
                Cyanidin.mapperManager.onClientYsmPacketReply(this.player);
            }
        }

        return ProxyComputeResult.ofPass();
    }
}
