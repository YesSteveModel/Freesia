package meow.kikir.freesia.velocity.events;

import java.util.UUID;

/**
 * 当worker成功连接到master会触发该事件
 */
public class WorkerConnectedEvent {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    /**
     * 获取worker的名字
     * @return worker的名字
     */
    public String getWorkerName() {
        return this.workerName;
    }

    /**
     * 获取worker的UUID
     * @return worker的UUID
     */
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}
