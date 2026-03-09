package meow.kikir.freesia.waterfall.events;

import java.util.UUID;

import net.md_5.bungee.api.plugin.Event;

/**
 * 当worker成功连接到master会触发该事件
 */
public class WorkerConnectedEvent extends Event {
    private final UUID workerUUID;
    private final String workerName;

    public WorkerConnectedEvent(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    /**
     * 获取worker的名字
     * 
     * @return worker的名字
     */
    public String getWorkerName() {
        return this.workerName;
    }

    /**
     * 获取worker的UUID
     * 
     * @return worker的UUID
     */
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }
}
