package gg.earthme.cyanidin.cyanidinbackend.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public class CyanidinTrackerScanEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<UUID> results;
    private final Player viewer;

    public CyanidinTrackerScanEvent(Set<UUID> results, Player viewer) {
        this.results = results;
        this.viewer = viewer;
    }

    public Player getViewer() {
        return this.viewer;
    }

    public Set<UUID> getResultsModifiable() {
        return this.results;
    }

    public Set<UUID> getResultsUnmodifiable() {
        return Set.copyOf(this.results);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
