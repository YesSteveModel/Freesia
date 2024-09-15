package gg.earthme.cyanidin.cyanidin.events;

import java.util.UUID;

/**
 * 加载玩家数据时会触发该事件
 */
public class PlayerEntityDataLoadEvent {
    private final UUID player;
    private byte[] serializedNbtData;

    public PlayerEntityDataLoadEvent(UUID player, byte[] serializedNbtData) {
        this.player = player;
        this.serializedNbtData = serializedNbtData;
    }

    public UUID getPlayer() {
        return this.player;
    }

    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }
}
