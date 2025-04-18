package meow.kikir.freesia.velocity.network.ysm;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.velocity.Freesia;
import net.kyori.adventure.key.Key;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VirtualYsmPacketProxyImpl extends YsmPacketProxyLayer {
    public VirtualYsmPacketProxyImpl(UUID virtualPlayerUUID) {
        super(virtualPlayerUUID);
    }

    @Override
    public CompletableFuture<Set<UUID>> fetchTrackerList(UUID observer) {
        return Freesia.tracker.getCanSee(observer);
    }

    @Override
    public ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }

    @Override
    public ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData) {
        return null;
    }
}
