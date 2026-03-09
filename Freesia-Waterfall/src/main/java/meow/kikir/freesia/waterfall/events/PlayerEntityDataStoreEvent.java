package meow.kikir.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * Triggered when player data is saved
 */
public class PlayerEntityDataStoreEvent extends Event {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataStoreEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * Gets the NBT data that will be saved
     * 
     * @return Encoded NBT (unnamed)
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     * Sets the final data to enter the data storage
     * 
     * @param serializedNbtData Encoded NBT (unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * Gets the UUID of the player holding this data
     * 
     * @return The player's UUID
     */
    public UUID getPlayer() {
        return this.player;
    }
}
