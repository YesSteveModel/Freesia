package meow.kikir.freesia.waterfall.events;

import com.google.common.annotations.Beta;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;

import meow.kikir.freesia.waterfall.network.ysm.YsmState;

/**
 * 当玩家更改模型时或worker设置玩家时该事件会被触发
 * 获取到的Nbt是要发送给玩家的
 * 注意:修改过后的nbt并不会被持久化即只会在当前进程发生作用而在重启后失效
 */
@Beta
public class PlayerEntityStateChangeEvent extends Event {
    private final ProxiedPlayer actualPlayer;
    private final int entityId;
    private final YsmState entityState;

    public PlayerEntityStateChangeEvent(ProxiedPlayer actualPlayer, int entityId, YsmState entityState) {
        this.actualPlayer = actualPlayer;
        this.entityId = entityId;
        this.entityState = entityState;
    }

    /**
     * 获取持有这个数据的玩家
     *
     * @return 玩家
     */
    public ProxiedPlayer getPlayer() {
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
     * @return 实体数据的包装形式
     */
    public YsmState getEntityState() {
        return this.entityState;
    }
}
