package meow.kikir.freesia.velocity.events;

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

    /**
     * 获取被加载玩家的UUID
     * 注意: 在此事件触发时，服务端可能仍在处理ServerConnectedEvent,所以此时无法通过该UUID获取到velocity上的Player对象
     * @see com.velocitypowered.api.event.player.ServerConnectedEvent
     * @return 被加载玩家的UUID
     */
    public UUID getPlayer() {
        return this.player;
    }

    /**
     * 获取未解码的NBT数据(unnamed)
     * @return
     */
    public byte[] getSerializedNbtData() {
        return this.serializedNbtData;
    }

    /**
     *设置最终的NBT数据
     * @param serializedNbtData 已经编码的UUID数据(unnamed)
     */
    public void setSerializedNbtData(byte[] serializedNbtData) {
        this.serializedNbtData = serializedNbtData;
    }
}
