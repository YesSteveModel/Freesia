package meow.kikir.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * Triggered when a worker successfully connects to the master
 */
public class WorkerConnectedEvent extends Event {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    /**
     * Gets the name of the worker
     * 
     * @return The worker's name
     */
    public String getWorkerName() {
        return this.workerName;
    }

    /**
     * Gets the UUID of the worker
     * 
     * @return The worker's UUID
     */
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}
