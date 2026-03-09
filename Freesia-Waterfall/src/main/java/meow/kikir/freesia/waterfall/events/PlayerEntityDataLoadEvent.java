package meow.kikir.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * Triggered when player data is loaded
 */
public class PlayerEntityDataLoadEvent extends Event {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataLoadEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * Gets the UUID of the loaded player
     * Note: When this event is triggered, the server may still be processing
     * conditions, so the Player object on the proxy cannot be retrieved using this
     * UUID yet
     * 
     * @see net.md_5.bungee.api.event.ServerConnectedEvent
     * @return The loaded player's UUID
     */
    public UUID getPlayer() {
        return this.player;
    }

    /**
     * Gets the unencoded NBT data (unnamed)
     * 
     * @return Encoded NBT (unnamed)
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     * Sets the final NBT data
     * 
     * @param serializedNbtData Encoded UUID data (unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }
}
