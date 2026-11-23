package meow.kikir.freesia.velocity.network.backend;

import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerBackendInfo {
    private final AtomicInteger activeMappersCount = new AtomicInteger(0);
    private final InetSocketAddress connectAddress;

    public WorkerBackendInfo(InetSocketAddress connectAddress) {
        this.connectAddress = connectAddress;
    }

    @NotNull
    public InetSocketAddress getConnectAddress() {
        return this.connectAddress;
    }

    public int getActiveMappers() {
        return this.activeMappersCount.get();
    }

    public void increaseActiveMapperCount() {
        this.activeMappersCount.getAndIncrement();
    }

    public void decreaseActiveMapperCount() {
        this.activeMappersCount.getAndDecrement();
    }
}
