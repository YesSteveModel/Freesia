package meow.kikir.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * 玩家数据保存时触发该事件
 */
public class PlayerEntityDataStoreEvent extends Event {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataStoreEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * 获取将要被保存的NBT数据
     * 
     * @return 已编码的NBT(unnamed)
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     * 设置最终进入datastorage的数据
     * 
     * @param serializedNbtData 已编码的NBT(unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }

    /**
     * 获取持有该数据的玩家的UUID
     * 
     * @return 玩家UUID
     */
    public UUID getPlayer() {
        return this.player;
    }
}
