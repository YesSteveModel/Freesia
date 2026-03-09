package com.nguyendevs.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * å½“workeræˆåŠŸè¿žæŽ¥åˆ°masterä¼šè§¦å‘è¯¥äº‹ä»¶
 */
public class WorkerConnectedEvent extends Event {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    /**
     * èŽ·å–workerçš„åå­—
     * 
     * @return workerçš„åå­—
     */
    public String getWorkerName() {
        return this.workerName;
    }

    /**
     * èŽ·å–workerçš„UUID
     * 
     * @return workerçš„UUID
     */
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}

