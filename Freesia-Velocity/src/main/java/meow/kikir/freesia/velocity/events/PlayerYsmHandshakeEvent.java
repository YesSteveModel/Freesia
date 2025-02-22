package meow.kikir.freesia.velocity.events;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * Ysm玩家的握手事件
 * 注意: 阻塞事件
 */
public class PlayerYsmHandshakeEvent implements ResultedEvent<ResultedEvent.GenericResult> {
    private GenericResult result = GenericResult.allowed();
    private final Player player;

    public PlayerYsmHandshakeEvent(Player player) {
        this.player = player;
    }

    /**
     * 获取当前的玩家
     * @return 玩家
     */
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public GenericResult getResult() {
        return this.result;
    }

    @Override
    public void setResult(GenericResult result) {
        this.result = result;
    }
}
