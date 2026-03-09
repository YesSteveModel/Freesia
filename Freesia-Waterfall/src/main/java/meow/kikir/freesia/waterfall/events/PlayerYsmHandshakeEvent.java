package meow.kikir.freesia.waterfall.events;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Cancellable;

/**
 * Ysm玩家的握手事件
 * 注意: 阻塞事件
 */
public class PlayerYsmHandshakeEvent extends Event implements Cancellable {
    private final ProxiedPlayer player;
    private boolean cancelled = false;

    public PlayerYsmHandshakeEvent(ProxiedPlayer player, boolean initialAllowed) {
        this.player = player;
        this.cancelled = !initialAllowed;
    }

    /**
     * 获取当前的玩家
     *
     * @return 玩家
     */
    public ProxiedPlayer getPlayer() {
        return this.player;
    }

    public boolean isAllowed() {
        return !this.cancelled;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
