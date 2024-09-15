package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.events.PlayerYsmHandshakeEvent;
import gg.earthme.cyanidin.cyanidin.events.PlayerEntityStateChangeEvent;
import gg.earthme.cyanidin.cyanidin.network.mc.NbtRemapper;
import gg.earthme.cyanidin.cyanidin.network.mc.impl.StandardNbtRemapperImpl;
import gg.earthme.cyanidin.cyanidin.utils.FriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

public class DefaultYsmPacketProxyImpl implements YsmPacketProxy{
    private final Player player;
    private final NbtRemapper nbtRemapper = new StandardNbtRemapperImpl();
    private volatile NBTCompound lastYsmEntityStatus = null;

    public DefaultYsmPacketProxyImpl(@NotNull Player player) {
        this.player = player;
    }

    @Override
    public void blockUntilProxyReady(){
        // Must block until the player joined to the backend server
        while (Cyanidin.mapperManager.getRealPlayerEntityId(this.player) == -1){
            Thread.yield();
            LockSupport.parkNanos(1_000);
        }
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

    @Override
    public void sendEntityStateTo(@NotNull Player target){
        final int currentEntityId = Cyanidin.mapperManager.getRealPlayerEntityId(this.player); // Get current entity id on the server of the player

        final NBTCompound lastEntityStatusTemp = this.lastYsmEntityStatus; // Copy the value instead of the reference

        if (lastEntityStatusTemp == null || currentEntityId == -1){ // If no data got or player is not in the backend server currently
            return;
        }

        try {
            final Object targetChannel = PacketEvents.getAPI().getProtocolManager().getChannel(target.getUniqueId()); // Get the netty channel of the player

            if (targetChannel == null){
                return;
            }

            final ClientVersion clientVersion = PacketEvents.getAPI().getProtocolManager().getClientVersion(targetChannel); // Get the client version of the player

            final int targetProtocolVer = clientVersion.getProtocolVersion(); // Protocol version(Used for nbt remappers)
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(currentEntityId);
            wrappedPacketData.writeBytes(this.nbtRemapper.shouldRemap(targetProtocolVer) ? this.nbtRemapper.remapToMasterVer(lastEntityStatusTemp) : this.nbtRemapper.remapToWorkerVer(lastEntityStatusTemp)); // Remap nbt if needed

            this.sendPluginMessageTo(target, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        }catch (Exception e){
            Cyanidin.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public void setEntityDataRaw(NBTCompound data) {
        this.lastYsmEntityStatus = data;
    }

    @Override
    public void refreshToOthers() {
        this.sendEntityStateTo(this.player); // Sync to self

        Cyanidin.tracker.getCanSee(this.player.getUniqueId()).whenComplete((beingWatched, exception) -> { // Async tracker check request to backend server
            if (beingWatched != null){ // Actually there is impossible to be null
                for (UUID targetUUID : beingWatched){
                    final Optional<Player> targetNullable = Cyanidin.PROXY_SERVER.getPlayer(targetUUID);

                    if (targetNullable.isPresent()){ // Skip send to NPCs
                        final Player target = targetNullable.get();

                        if (!Cyanidin.mapperManager.isPlayerInstalledYsm(target)){ // Skip if target is not ysm-installed
                            continue;
                        }

                        this.sendEntityStateTo(target); // Sync to target
                    }
                }
            }
        });
    }

    @Override
    public NBTCompound getCurrentEntityState() {
        return this.lastYsmEntityStatus;
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
                    if (!this.isEntityStateOfSelf(workerEntityId)){ // Check if the packet is current player and drop to prevent incorrect broadcasting
                        return ProxyComputeResult.ofDrop(); // Do not process the entity state if it is not ours
                    }

                    Cyanidin.PROXY_SERVER.getEventManager().fire(new PlayerEntityStateChangeEvent(this.player,workerEntityId, this.nbtRemapper.readBound(mcBuffer))).thenAccept(result -> {
                        this.lastYsmEntityStatus = result.getEntityState(); // Read using the protocol version matched for the worker

                        this.sendEntityStateTo(this.player); // Sync to self

                        Cyanidin.tracker.getCanSee(this.player.getUniqueId()).whenComplete((beingWatched, exception) -> { // Async tracker check request to backend server
                            if (beingWatched != null){ // Actually there is impossible to be null
                                for (UUID targetUUID : beingWatched){
                                    final Optional<Player> targetNullable = Cyanidin.PROXY_SERVER.getPlayer(targetUUID);

                                    if (targetNullable.isPresent()){ // Skip send to NPCs
                                        final Player target = targetNullable.get();

                                        if (!Cyanidin.mapperManager.isPlayerInstalledYsm(target)){ // Skip if target is not ysm-installed
                                            continue;
                                        }

                                        this.sendEntityStateTo(target); // Sync to target
                                    }
                                }
                            }
                        });
                    });
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Error while in processing tracker!", e);
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

        if (packetId == 52) {
            final ResultedEvent.GenericResult result = Cyanidin.PROXY_SERVER.getEventManager().fire(new PlayerYsmHandshakeEvent(this.player)).join().getResult();

            if (!result.isAllowed()){
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            Cyanidin.LOGGER.info("Player {} is connected to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
            Cyanidin.mapperManager.onClientYsmHandshakePacketReply(this.player);
        }

        return ProxyComputeResult.ofPass();
    }
}
