package meow.kikir.freesia.waterfall.events;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.api.plugin.Cancellable;

/**
 * YSM player handshake event
 * Note: Blocking event
 */
public class PlayerYsmHandshakeEvent extends Event implements Cancellable {
    private final ProxiedPlayer player;
    private boolean cancelled = false;

    public PlayerYsmHandshakeEvent(ProxiedPlayer player, boolean initialAllowed) {
        this.player = player;
        this.cancelled = !initialAllowed;
    }

    /**
     * Gets the current player
     * 
     * @return The player
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
