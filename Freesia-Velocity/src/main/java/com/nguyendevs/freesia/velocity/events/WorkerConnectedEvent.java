package com.nguyendevs.freesia.velocity.events;

import java.util.UUID;

/**
 * å½“workeræˆåŠŸè¿žæŽ¥åˆ°masterä¼šè§¦å‘è¯¥äº‹ä»¶
 */
public class WorkerConnectedEvent {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    /**
     * èŽ·å–workerçš„åå­—
     * @return workerçš„åå­—
     */
    public String getWorkerName() {
        return this.workerName;
    }

    /**
     * èŽ·å–workerçš„UUID
     * @return workerçš„UUID
     */
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}

