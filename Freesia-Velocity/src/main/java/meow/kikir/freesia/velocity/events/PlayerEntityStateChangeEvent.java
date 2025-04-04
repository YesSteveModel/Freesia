package meow.kikir.freesia.velocity.events;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.google.common.annotations.Beta;
import com.velocitypowered.api.proxy.Player;

/**
 * 当玩家更改模型时或worker设置玩家时该事件会被触发
 * 获取到的Nbt是要发送给玩家的
 * 注意:修改过后的nbt并不会被持久化即只会在当前进程发生作用而在重启后失效
 */
@Beta
public class PlayerEntityStateChangeEvent {
    private final Player actualPlayer;
    private final int entityId;
    private final NBTCompound entityState;

    public PlayerEntityStateChangeEvent(Player actualPlayer, int entityId, NBTCompound entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    /**
     * 获取持有这个数据的玩家
     *
     * @return 玩家
     */
    public Player getPlayer() {
        return this.actualPlayer;
    }

    /**
     * 获取这个玩家在worker侧的实体id
     *
     * @return 实体id
     */
    public int getEntityId() {
        return this.entityId;
    }

    /**
     * 获取玩家的ysm实体数据
     *
     * @return 实体数据的nbt形式
     */
    public NBTCompound getEntityState() {
        return this.entityState;
    }
}
