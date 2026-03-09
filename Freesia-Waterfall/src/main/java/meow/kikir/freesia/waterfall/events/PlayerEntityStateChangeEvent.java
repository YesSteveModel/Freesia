package meow.kikir.freesia.waterfall.events;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.google.common.annotations.Beta;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;

/**
 * Triggered when a player changes their model or a worker updates the player
 * The retrieved NBT is intended to be sent to the player
 * Note: Modified NBT will not be persisted, meaning it only affects the current
 * process and is lost after a restart
 */
@Beta
public class PlayerEntityStateChangeEvent extends Event {
    private final ProxiedPlayer actualPlayer;
    private final int entityId;
    private final NBTCompound entityState;

    public PlayerEntityStateChangeEvent(ProxiedPlayer actualPlayer, int entityId, NBTCompound entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    /**
     * Gets the player holding this data
     *
     * @return The player
     */
    public ProxiedPlayer getPlayer() {
        return this.actualPlayer;
    }

    /**
     * Gets the entity ID of this player on the worker side
     *
     * @return The entity ID
     */
    public int getEntityId() {
        return this.entityId;
    }

    /**
     * Gets the player's YSM entity data
     *
     * @return The NBT format of the entity data
     */
    public NBTCompound getEntityState() {
        return this.entityState;
    }
}
