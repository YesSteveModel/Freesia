package meow.kikir.freesia.velocity.network.ysm;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.google.errorprone.annotations.Var;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.FreesiaConstants;
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

import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.StampedLock;

public class DefaultYsmPacketProxyImpl implements YsmPacketProxy{
    private final Player player;
    private final NbtRemapper nbtRemapper = new StandardNbtRemapperImpl();

    private volatile NBTCompound lastYsmEntityStatus = null;
    private volatile boolean proxyReady = false;

    private final StampedLock entityStatusWriteLock = new StampedLock(); // Use optimistic locks

    private volatile int playerEntityId = -1;
    private volatile int workerPlayerEntityId = -1;

    private MapperSessionProcessor parentHandler;

    private static final VarHandle WORKER_PLAYER_ENTITY_ID_VARHANDLE = ConcurrentUtil.getVarHandle(DefaultYsmPacketProxyImpl.class, "workerPlayerEntityId", int.class);
    private static final VarHandle PLAYER_ENTITY_ID_VARHANDLE = ConcurrentUtil.getVarHandle(DefaultYsmPacketProxyImpl.class, "playerEntityId", int.class);
    private static final VarHandle PROXY_READY_VARHANDLE = ConcurrentUtil.getVarHandle(DefaultYsmPacketProxyImpl.class, "proxyReady", boolean.class);
    private static final VarHandle LAST_YSM_ENTITY_STATUS_VARHANDLE = ConcurrentUtil.getVarHandle(DefaultYsmPacketProxyImpl.class, "lastYsmEntityStatus", NBTCompound.class);

    public DefaultYsmPacketProxyImpl(@NotNull Player player) {
        this.player = player;
    }

    @Override
    public void setParentHandler(MapperSessionProcessor handler) {
        this.parentHandler = handler;
    }

    @Override
    public void setPlayerWorkerEntityId(int id) {
        WORKER_PLAYER_ENTITY_ID_VARHANDLE.setVolatile(this, id);
    }

    @Override
    public void setPlayerEntityId(int id) {
        final boolean updated = PLAYER_ENTITY_ID_VARHANDLE.compareAndSet(this, -1, id);

        if (updated && id != -1) {
            // Try sync tracker status once
            // We could hardly say there is no out-of-ordering issue here
            // So we had better to force fire the update
            this.refreshToOthers();
        }
    }

    @Override
    public int getPlayerEntityId() {
        return (int) PLAYER_ENTITY_ID_VARHANDLE.getVolatile(this);
    }

    @Override
    public int getPlayerWorkerEntityId() {
        return (int) WORKER_PLAYER_ENTITY_ID_VARHANDLE.getVolatile(this);
    }

    @Override
    public Player getOwner() {
        return this.player;
    }

    private boolean isEntityStateOfSelf(int entityId){
        final int currentWorkerEntityId = (int) WORKER_PLAYER_ENTITY_ID_VARHANDLE.getVolatile(this);

        if (currentWorkerEntityId == -1) {
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    @Override
    public void sendEntityStateTo(@NotNull Player target){
        this.sendEntityStateToInternal(target, (NBTCompound) LAST_YSM_ENTITY_STATUS_VARHANDLE.getVolatile(this));
    }

    private void sendEntityStateToInternal(Player target, NBTCompound entityStatus) {
        final int currentEntityId = (int) PLAYER_ENTITY_ID_VARHANDLE.getVolatile(this); // Get current entity id on the server of the player

        if (entityStatus == null || currentEntityId == -1) { // If no data got or player is not in the backend server currently
            return;
        }

        try {
            final Object targetChannel = PacketEvents.getAPI().getProtocolManager().getChannel(target.getUniqueId()); // Get the netty channel of the player

            if (targetChannel == null) {
                return;
            }

            final ClientVersion clientVersion = PacketEvents.getAPI().getProtocolManager().getClientVersion(targetChannel); // Get the client version of the player

            final int targetProtocolVer = clientVersion.getProtocolVersion(); // Protocol version(Used for nbt remappers)
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(currentEntityId);
            wrappedPacketData.writeBytes(this.nbtRemapper.shouldRemap(targetProtocolVer) ? this.nbtRemapper.remapToMasterVer(entityStatus) : this.nbtRemapper.remapToWorkerVer(entityStatus)); // Remap nbt if needed

            this.sendPluginMessageTo(target, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        } catch (Exception e) {
            Freesia.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public void setEntityDataRaw(NBTCompound data) {
        LAST_YSM_ENTITY_STATUS_VARHANDLE.setVolatile(this, data);
    }

    @Override
    public void refreshToOthers() {
        final NBTCompound entityStatusCopy = (NBTCompound) LAST_YSM_ENTITY_STATUS_VARHANDLE.getVolatile(this); // Copy value

        // If the player does not have any data
        if (entityStatusCopy == null || (int)PLAYER_ENTITY_ID_VARHANDLE.getVolatile(this) == -1) {
            return;
        }

        // Prevent race condition
        if (!PROXY_READY_VARHANDLE.compareAndSet(this, false, true)){
            this.parentHandler.retireTrackerCallbacks(); // Retire the tracker callbacks
        }

        this.sendEntityStateToInternal(this.player, entityStatusCopy); // Sync to self

        Freesia.tracker.getCanSee(this.player.getUniqueId()).whenComplete((beingWatched, exception) -> { // Async tracker check request to backend server
            // Exception handling
            if (exception != null) {
                Freesia.LOGGER.warn("Failed to fetch tracker info!", exception);
                return;
            }

            if (beingWatched != null) { // When backend is not fully initialized yet or other reasons
                for (UUID targetUUID : beingWatched) {
                    final Optional<Player> targetNullable = Freesia.PROXY_SERVER.getPlayer(targetUUID);

                    if (targetNullable.isPresent()) { // Skip send to NPCs
                        final Player target = targetNullable.get();

                        if (!Freesia.mapperManager.isPlayerInstalledYsm(target)) { // Skip if target is not ysm-installed
                            continue;
                        }

                        this.sendEntityStateToInternal(target, entityStatusCopy); // Sync to target
                    }
                }
            }
        });
    }

    @Override
    public NBTCompound getCurrentEntityState() {
        return (NBTCompound) LAST_YSM_ENTITY_STATUS_VARHANDLE.getVolatile(this);
    }

    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final FriendlyByteBuf mcBuffer = new FriendlyByteBuf(copiedPacketData);
        final byte packetId = mcBuffer.readByte();

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.ENTITY_DATA_UPDATE)) {
            final int workerEntityId = mcBuffer.readVarInt();

            try {
                if (!this.isEntityStateOfSelf(workerEntityId)) { // Check if the packet is current player and drop to prevent incorrect broadcasting
                    return ProxyComputeResult.ofDrop(); // Do not process the entity state if it is not ours
                }

                Freesia.PROXY_SERVER.getEventManager().fire(new PlayerEntityStateChangeEvent(this.player,workerEntityId, this.nbtRemapper.readBound(mcBuffer))).thenAccept(result -> { // Use NbtRemapper for multi version clients
                    final NBTCompound to = result.getEntityState();

                    NBTCompound curr = (NBTCompound) LAST_YSM_ENTITY_STATUS_VARHANDLE.getVolatile(this);
                    while (!LAST_YSM_ENTITY_STATUS_VARHANDLE.compareAndSet(this, curr, to)){
                        curr = (NBTCompound) LAST_YSM_ENTITY_STATUS_VARHANDLE.getVolatile(this);
                    }


                    this.refreshToOthers();
                }).join(); // Force blocking as we do not wanna break the sequence of the data
            } catch (Exception e) {
                Freesia.LOGGER.error("Error while in processing tracker!", e);
                return ProxyComputeResult.ofDrop();
            }

            return ProxyComputeResult.ofDrop();
        }

        if (packetId == YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.HAND_SHAKE_CONFIRMED)) {
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

        if (packetId == YsmProtocolMetaFile.getC2SPacketId(FreesiaConstants.YsmProtocolMetaConstants.Serverbound.HAND_SHAKE_REQUEST)) {
            final ResultedEvent.GenericResult result = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerYsmHandshakeEvent(this.player)).join().getResult();

            if (!result.isAllowed()) {
                return ProxyComputeResult.ofDrop();
            }

            final String clientYsmVersion = mcBuffer.readUtf();
            Freesia.LOGGER.info("Player {} is connected to the backend with ysm version {}", this.player.getUsername(), clientYsmVersion);
            Freesia.mapperManager.onClientYsmHandshakePacketReply(this.player);
        }

        return ProxyComputeResult.ofPass();
    }
}
