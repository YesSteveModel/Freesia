package meow.kikir.freesia.velocity.network.ysm;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.velocity.network.mc.NbtRemapper;
import meow.kikir.freesia.velocity.network.mc.impl.StandardNbtRemapperImpl;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The framework which is used for all ysm packet proxies</br>
 * Note:</br>
 * <p>
 *     1.This object is only can be used for once, any duplicated entity id and worker entity id updates will be ignored
 * because it cannot and should not be updated multiple times as we just use them for per mappers</p>
 * <p>
 *     2.Any write operations of ysm entity data is ONLY single thread safe!!!</p>
 *
 */
public abstract class YsmPacketProxyLayer implements YsmPacketProxy{
    protected final Player player;

    protected final UUID playerUUID;
    protected final NbtRemapper nbtRemapper = new StandardNbtRemapperImpl();

    protected volatile MapperSessionProcessor handler;

    private int playerEntityId = -1;
    private int workerEntityId = -1;

    private int entityDataReferenceCount = 0; // Used for read-writing lock but writing is always happening on a single thread
    private NBTCompound lastYsmEntityData = null;

    private boolean proxyReady = false;

    protected static final VarHandle ENTITY_DATA_REF_COUNT_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class, "entityDataReferenceCount", int.class);
    protected static final VarHandle PROXY_READY_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class, "proxyReady", boolean.class);

    protected static final VarHandle PLAYER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class, "playerEntityId", int.class);
    protected static final VarHandle WORKER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class, "workerEntityId", int.class);

    protected static final VarHandle LAST_YSM_ENTITY_DATA_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyLayer.class, "lastYsmEntityData", NBTCompound.class);

    protected YsmPacketProxyLayer(UUID playerUUID) {
        this.player = Freesia.PROXY_SERVER.getPlayer(playerUUID).orElse(null); // Get if it is a real player
        this.playerUUID = playerUUID;
    }

    protected YsmPacketProxyLayer(@NotNull Player player) {
        this.player = player;
        this.playerUUID = player.getUniqueId();
    }

    // Read and write locks for entity data, we just use them for very, very short term operations so there is no need to
    // worry the thread contention issue on performance
    protected void releaseWriteReference() {
        // There is no any thread contention because the write locks are currently in our hands
        if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, -1, 0)) {
            throw new IllegalStateException("Releasing when not write-locked");
        }
    }

    protected void acquireWriteReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            // Reading or another writing operations are not finished
            if (curr > 0 || curr == -1) {
                failureCount++;
                continue;
            }

            // Another thread is acquiring write or read reference
            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, -1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    protected void releaseReadReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            if (curr == -1) {
                throw new IllegalStateException("Cannot release read reference when write locked");
            }

            if (curr == 0) {
                throw new IllegalStateException("Setting reference count down to a value lower than 0!");
            }

            // Another thread is acquiring read reference
            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, curr - 1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    protected void acquireReadReference() {
        int failureCount = 0;
        for (;;) {
            for (int i = 0; i < failureCount; i++) {
                ConcurrentUtil.backoff();
            }

            final int curr = (int) ENTITY_DATA_REF_COUNT_HANDLE.getVolatile(this);

            // Write locked
            if (curr == -1) {
                failureCount++;
                continue;
            }

            // Another thread is acquiring read or write reference
            if (!ENTITY_DATA_REF_COUNT_HANDLE.compareAndSet(this, curr, curr + 1)) {
                failureCount++;
                continue;
            }

            break;
        }
    }

    // Write and read locks end

    @Nullable
    @Override
    public Player getOwner() {
        return this.player;
    }

    protected boolean isEntityStateOfSelf(int entityId){
        final int currentWorkerEntityId = (int) WORKER_ENTITY_ID_HANDLE.getVolatile(this);

        if (currentWorkerEntityId == -1) {
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    @Override
    public void setParentHandler(MapperSessionProcessor handler) {
        this.handler = handler;
    }

    @Override
    public void sendEntityStateTo(@NotNull Player target) {
        this.acquireReadReference(); // Acquire read reference

        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);
        final NBTCompound currEntityData = (NBTCompound) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);

        this.releaseReadReference(); // Release when we copied the value

        // Not fully initialized yet
        if (currEntityId == -1 || currEntityData == null) {
            return;
        }

        this.sendEntityStateToRaw(target.getUniqueId(), currEntityId, currEntityData);
    }

    @Override
    public void setEntityDataRaw(NBTCompound data) {
        LAST_YSM_ENTITY_DATA_HANDLE.setVolatile(this, data);
    }

    @Override
    public void notifyFullTrackerUpdates() {
        this.acquireReadReference(); // Acquire read reference

        final NBTCompound currEntityData = (NBTCompound) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);
        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        this.releaseReadReference(); // Release when we copied the value

        // Not fully initialized yet
        if (currEntityId == -1 || currEntityData == null) {
            return;
        }

        // Prevent race condition
        if (PROXY_READY_HANDLE.compareAndSet(this, false, true)) {
            // If we have the mapper connection
            if (this.handler != null) {
                // Done queued tracker updates
                this.handler.retireTrackerCallbacks();
            }
        }

        // Sync to the owner self
        this.sendEntityStateToRaw(this.playerUUID, currEntityId, currEntityData);

        // Fetch can-see list
        this.fetchTrackerList(this.playerUUID).whenComplete((result, ex) -> {
            // Exception processing
            if (ex != null) {
                Freesia.LOGGER.warn("Failed to fetch tracker list for player uuid {}: {}", this.player != null ? this.player.getUniqueId(): this.playerUUID, ex);
                return;
            }

            for (UUID toSend : result) {
                final Optional<Player> queryResult = Freesia.PROXY_SERVER.getPlayer(toSend);

                // If it is a real player
                if (queryResult.isPresent()) {
                    // Check ysm installed
                    if (Freesia.mapperManager.isPlayerInstalledYsm(toSend)) {
                        this.sendEntityStateToRaw(toSend, currEntityId, currEntityData);
                    }
                }
            }
        });
    }

    public abstract CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer);

    @Override
    public void executeMolang(String expression) {
        final int playerEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        if (playerEntityId == -1 || this.player == null) {
            return;
        }

        final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());
        wrappedPacketData.writeByte(YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE));
        wrappedPacketData.writeVarIntArray(new int[]{playerEntityId}); // Write the entity id
        wrappedPacketData.writeUtf(expression); // Write the expression

        this.sendPluginMessageToOwner(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
    }

    @Override
    public void executeMolang(int[] entityIds, String expression) {
        if (this.player == null) {
            return;
        }

        final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());
        wrappedPacketData.writeByte(YsmProtocolMetaFile.getS2CPacketId(FreesiaConstants.YsmProtocolMetaConstants.Clientbound.MOLANG_EXECUTE));
        wrappedPacketData.writeVarIntArray(entityIds); // Write the entity id
        wrappedPacketData.writeUtf(expression); // Write the expression

        this.sendPluginMessageToOwner(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
    }

    protected void sendEntityStateToRaw(@NotNull UUID receiverUUID, int entityId, NBTCompound data) {
        try {
            final Optional<Player> queryResult = Freesia.PROXY_SERVER.getPlayer(receiverUUID);

            if (queryResult.isEmpty()) {
                return;
            }

            final Player receiver = queryResult.get();
            final Object targetChannel = PacketEvents.getAPI().getProtocolManager().getChannel(receiver.getUniqueId()); // Get the netty channel of the player

            if (targetChannel == null) {
                return;
            }

            final ClientVersion clientVersion = PacketEvents.getAPI().getProtocolManager().getClientVersion(targetChannel); // Get the client version of the player

            final int targetProtocolVer = clientVersion.getProtocolVersion(); // Protocol version(Used for nbt remappers)
            final FriendlyByteBuf wrappedPacketData = new FriendlyByteBuf(Unpooled.buffer());

            wrappedPacketData.writeByte(4);
            wrappedPacketData.writeVarInt(entityId);
            wrappedPacketData.writeBytes(this.nbtRemapper.shouldRemap(targetProtocolVer) ?
                    this.nbtRemapper.remapToMasterVer(data) :
                    this.nbtRemapper.remapToWorkerVer(data)
            ); // Remap nbt if needed

            this.sendPluginMessageTo(receiver, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, wrappedPacketData);
        } catch (Exception e) {
            Freesia.LOGGER.error("Error in encoding nbt or sending packet!", e);
        }
    }

    @Override
    public NBTCompound getCurrentEntityState() {
        return (NBTCompound) LAST_YSM_ENTITY_DATA_HANDLE.getVolatile(this);
    }

    @Override
    public void setPlayerWorkerEntityId(int id){
        // Only update for once
        final boolean successfullyUpdated = WORKER_ENTITY_ID_HANDLE.compareAndSet(this, -1, id);

        if (successfullyUpdated) {
            this.notifyFullTrackerUpdates(); // If it is the first update
        }
    }

    @Override
    public void setPlayerEntityId(int id) {
        // Only update for once
        final boolean successfullyUpdated = PLAYER_ENTITY_ID_HANDLE.compareAndSet(this, -1, id);

        if (successfullyUpdated) {
            this.notifyFullTrackerUpdates(); // If it is the first update
        }
    }

    @Override
    public int getPlayerEntityId() {
        return (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);
    }

    @Override
    public int getPlayerWorkerEntityId() {
        return (int) WORKER_ENTITY_ID_HANDLE.getVolatile(this);
    }
}
