package gg.earthme.cyanidin.cyanidinbackend.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CyanidinRealPlayerTrackerUpdateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Player watcher;
    private final Player beingWatched;

    public CyanidinRealPlayerTrackerUpdateEvent(Player watcher, Player beingWatched) {
        this.watcher = watcher;
        this.beingWatched = beingWatched;
    }

    public Player getWatcher() {
        return this.watcher;
    }

    public Player getBeingWatched() {
        return this.beingWatched;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
