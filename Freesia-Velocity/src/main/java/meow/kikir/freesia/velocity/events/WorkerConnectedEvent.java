package meow.kikir.freesia.velocity.events;

import java.util.UUID;

/**
 * 当worker成功连接到master会触发该事件
 */
public record WorkerConnectedEvent(UUID workerUUID, String workerName) {

    /**
     * 获取worker的名字
     *
     * @return worker的名字
     */
    @Override
    public String workerName() {
        return this.workerName;
    }

    /**
     * 获取worker的UUID
     *
     * @return worker的UUID
     */
    @Override
    public UUID workerUUID() {
        return this.workerUUID;
    }
}
