package meow.kikir.freesia.velocity.network.ysm;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CAnimationDataUpdatePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CModelDataUpdatePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CMolangExecutePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.UUID;

/**
 * The framework which is used for all ysm packet proxies</br>
 * Note:</br>
 * <p>
 *     1.This object is only can be used for once, any duplicated entity id and worker entity id updates will be ignored
 * because it cannot and should not be updated multiple times as we just use them for per mappers</p>
 * <p>
 *
 */
public abstract class YsmPacketProxyBase implements YsmPacketProxy{
    protected final Player player;
    protected final UUID playerUUID;

    protected volatile MapperConnectionHandler handler;

    private int playerEntityId = -1;
    private int workerEntityId = -1;

    private int entityDataReferenceCount = 0;
    private byte[] lastYsmModelData = null;
    private byte[] lastYsmAnimationData = null;

    private boolean proxyReady = false;

    protected static final VarHandle ENTITY_DATA_REF_COUNT_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "entityDataReferenceCount", int.class);
    protected static final VarHandle PROXY_READY_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "proxyReady", boolean.class);

    protected static final VarHandle PLAYER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "playerEntityId", int.class);
    protected static final VarHandle WORKER_ENTITY_ID_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "workerEntityId", int.class);

    protected static final VarHandle LAST_YSM_MODEL_DATA_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "lastYsmModelData", byte[].class);
    protected static final VarHandle LAST_YSM_ANIMATION_DATA_HANDLE = ConcurrentUtil.getVarHandle(YsmPacketProxyBase.class, "lastYsmAnimationData", byte[].class);

    protected YsmPacketProxyBase(UUID playerUUID) {
        this.player = Freesia.PROXY_SERVER.getPlayer(playerUUID).orElse(null); // Get if it is a real player
        this.playerUUID = playerUUID;
    }

    protected YsmPacketProxyBase(@NotNull Player player) {
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

    public boolean isEntityStateOfSelf(int entityId){
        final int currentWorkerEntityId = (int) WORKER_ENTITY_ID_HANDLE.getVolatile(this);

        if (currentWorkerEntityId == -1) {
            return false;
        }

        return currentWorkerEntityId == entityId;
    }

    @Override
    public void setParentHandler(MapperConnectionHandler handler) {
        this.handler = handler;
    }

    @Override
    public void sendFullEntityDataTo(@NotNull Player target) {
        this.acquireReadReference(); // Acquire read reference

        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);
        final byte[] currEntityData = (byte[]) LAST_YSM_MODEL_DATA_HANDLE.getVolatile(this);
        final byte[] currAnimationData = (byte[]) LAST_YSM_ANIMATION_DATA_HANDLE.getVolatile(this);

        this.releaseReadReference(); // Release when we copied the value

        // Not fully initialized yet
        if (currEntityId == -1 || currEntityData == null) {
            return;
        }

        this.sendModelDataToRaw(target, currEntityId, currEntityData);
        this.sendAnimationDataToRaw(target, currEntityId, currAnimationData);
    }

    @Override
    public void setModelDataRaw(byte[] data) {
        this.acquireWriteReference();
        LAST_YSM_MODEL_DATA_HANDLE.setVolatile(this, data);
        this.releaseWriteReference();
    }

    @Override
    public void setAnimationDataRaw(byte[] data) {
        this.acquireWriteReference();
        LAST_YSM_ANIMATION_DATA_HANDLE.setVolatile(this, data);
        this.releaseWriteReference();
    }

    @Override
    public byte[] getCurrentAnimationDataRaw(byte[] data) {
        this.acquireReadReference();
        try {
            return (byte[]) LAST_YSM_ANIMATION_DATA_HANDLE.getVolatile(this);
        } finally {
            this.releaseReadReference();
        }
    }

    @Override
    public void notifyFullTrackerUpdates() {
        this.acquireReadReference(); // Acquire read reference

        final byte[] currModelData = (byte[]) LAST_YSM_MODEL_DATA_HANDLE.getVolatile(this);
        final byte[] currAnimationData = (byte[]) LAST_YSM_ANIMATION_DATA_HANDLE.getVolatile(this);
        final int currEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        this.releaseReadReference(); // Release when we copied the value

        // Not fully initialized yet
        if (currEntityId == -1 || currModelData == null) {
            return;
        }

        // Prevent race condition
        if (PROXY_READY_HANDLE.compareAndSet(this, false, true)) {
            // If we have the mapper connection
            if (this.handler != null) {
                // Done queued tracker updates
                this.handler.retireTrackerCallbacks();
            }

            // retire proxy ready callback
            this.onProxyReadyCallback();
        }

        // Sync to the owner self
        this.sendModelDataToRaw(this.player, currEntityId, currModelData);
        this.sendAnimationDataToRaw(this.player, currEntityId, currAnimationData);

        // Fetch can-see list
        for (Player toSend : this.visiblePlayersTo(this.playerUUID)) {
            // Check ysm installed
            if (Freesia.mappersManager.isPlayerInstalledYsm(toSend)) {
                this.sendModelDataToRaw(toSend, currEntityId, currModelData);
                this.sendAnimationDataToRaw(toSend, currEntityId, currAnimationData);
            }
        }
    }

    public abstract Set<Player> visiblePlayersTo(UUID beingWatched);

    @Override
    public void executeMolang(String expression) {
        final int playerEntityId = (int) PLAYER_ENTITY_ID_HANDLE.getVolatile(this);

        if (playerEntityId == -1 || this.player == null) {
            return;
        }

        final S2CMolangExecutePacket molangExecutePacket = new S2CMolangExecutePacket(new int[]{playerEntityId}, expression);

        this.sendYsmPacket(molangExecutePacket);
    }

    @Override
    public void executeMolang(int[] entityIds, String expression) {
        if (this.player == null) {
            return;
        }

        final S2CMolangExecutePacket molangExecutePacket = new S2CMolangExecutePacket(entityIds, expression);

        this.sendYsmPacket(molangExecutePacket);
    }

    protected void sendModelDataToRaw(@NotNull Player receiver, int entityId, @Nullable byte[] data) {
        if (data == null) {
            return;
        }

        final S2CModelDataUpdatePacket modelDataUpdatePacket = new S2CModelDataUpdatePacket(entityId, data);
        this.sendYsmPacket(receiver, modelDataUpdatePacket);
    }

    protected void sendAnimationDataToRaw(@NotNull Player receiver, int entityId, byte @Nullable [] data) {
        if (data == null) {
            return;
        }

        final S2CAnimationDataUpdatePacket animationDataUpdatePacket = new S2CAnimationDataUpdatePacket(entityId, data);

        this.sendYsmPacket(receiver, animationDataUpdatePacket);
    }

    @Override
    public byte[] getCurrentModelData() {
        return (byte[]) LAST_YSM_MODEL_DATA_HANDLE.getVolatile(this);
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
