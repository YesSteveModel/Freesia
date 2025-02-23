package meow.kikir.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.velocity.events.PlayerYsmHandshakeEvent;
import meow.kikir.freesia.velocity.events.PlayerEntityStateChangeEvent;
import meow.kikir.freesia.velocity.network.mc.NbtRemapper;
import meow.kikir.freesia.velocity.network.mc.impl.StandardNbtRemapperImpl;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
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
        while (Freesia.mapperManager.getRealPlayerEntityId(this.player) == -1){
            Thread.yield();
            LockSupport.parkNanos(1_000);
        }
    }

    @Override
    public Player getOwner() {
        return this.player;
    }

    private boolean isEntityStateOfSelf(int entityId){
        final int currentWorkerEntityId = Freesia.mapperManager.getWorkerPlayerEntityId(this.player);

        if (currentWorkerEntityId == -1){
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    @Override
    public void sendEntityStateTo(@NotNull Player target){
        final int currentEntityId = Freesia.mapperManager.getRealPlayerEntityId(this.player); // Get current entity id on the server of the player

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
            Freesia.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public void setEntityDataRaw(NBTCompound data) {
        this.lastYsmEntityStatus = data;
    }

    @Override
    public void refreshToOthers() {
        // Impossible to be called here
        throw new UnsupportedOperationException();
    }

    @Override
    public NBTCompound getCurrentEntityState() {
        return this.lastYsmEntityStatus;
    }

    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(YsmProtocolMetaFile.ProtocolKeys.Clientbound.ENTITY_DATA_UPDATE)) {
            final int workerEntityId = mcBuffer.readVarInt();

            try {
                if (!this.isEntityStateOfSelf(workerEntityId)){ // Check if the packet is current player and drop to prevent incorrect broadcasting
                    return ProxyComputeResult.ofDrop(); // Do not process the entity state if it is not ours
                }

                Freesia.PROXY_SERVER.getEventManager().fire(new PlayerEntityStateChangeEvent(this.player,workerEntityId, this.nbtRemapper.readBound(mcBuffer))).thenAccept(result -> {
                    this.lastYsmEntityStatus = result.getEntityState(); // Read using the protocol version matched for the worker

                    this.sendEntityStateTo(this.player); // Sync to self

                    Freesia.tracker.getCanSee(this.player.getUniqueId()).whenComplete((beingWatched, exception) -> { // Async tracker check request to backend server
                        if (beingWatched != null){ // Actually there is impossible to be null
                            for (UUID targetUUID : beingWatched){
                                final Optional<Player> targetNullable = Freesia.PROXY_SERVER.getPlayer(targetUUID);

                                if (targetNullable.isPresent()){ // Skip send to NPCs
                                    final Player target = targetNullable.get();

                                    if (!Freesia.mapperManager.isPlayerInstalledYsm(target)){ // Skip if target is not ysm-installed
                                        continue;
                                    }

                                    this.sendEntityStateTo(target); // Sync to target
                                }
                            }
                        }
                    });
                });
            }catch (Exception e){
                Freesia.LOGGER.error("Error while in processing tracker!", e);
                return ProxyComputeResult.ofDrop();
            }

            return ProxyComputeResult.ofDrop();
        }

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(YsmProtocolMetaFile.ProtocolKeys.Clientbound.HAND_SHAKE_CONFIRMED)) {
            final String backendVersion = mcBuffer.readUtf();
            final boolean canSwitchModel = mcBuffer.readBoolean();

            Freesia.LOGGER.info("Replying ysm client with server version {}.Can switch model? : {}", backendVersion, canSwitchModel);

            return ProxyComputeResult.ofPass();
        }

        return ProxyComputeResult.ofPass();
    }

    @Override
    public ProxyComputeResult processC2S(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile.getC2SPacketId(YsmProtocolMetaFile.ProtocolKeys.Serverbound.HAND_SHAKE_REQUEST)) {
            final ResultedEvent.GenericResult result = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerYsmHandshakeEvent(this.player)).join().getResult();

            if (!result.isAllowed()){
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            Freesia.LOGGER.info("Player {} is connected to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
            Freesia.mapperManager.onClientYsmHandshakePacketReply(this.player);
        }

        return ProxyComputeResult.ofPass();
    }
}
