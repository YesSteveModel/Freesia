package meow.kikir.freesia.velocity.events;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;

/**
 * Ysm玩家的握手事件
 * 注意: 阻塞事件
 */
public class PlayerYsmHandshakeEvent implements ResultedEvent<ResultedEvent.GenericResult> {
    private final Player player;
    private final String clientYsmVersion;
    private GenericResult result = GenericResult.allowed();

    public PlayerYsmHandshakeEvent(Player player, String clientYsmVersion) {
        this.player = player;
        this.clientYsmVersion = clientYsmVersion;
    }

    /**
     * 获取当前的玩家
     *
     * @return 玩家
     */
    public Player getPlayer() {
        return this.player;
    }


    /**
     * 获取客户端的YSM版本
     *
     * @return YSM版本字符串
     */
    public String getClientYsmVersion() {
        return this.clientYsmVersion;
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
