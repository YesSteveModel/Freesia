package gg.earthme.cyanidin.cyanidinbackend.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public class CyanidinTrackerScanEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<UUID> results;

    public CyanidinTrackerScanEvent(Set<UUID> results) {
        super(true);
        this.results = results;
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
