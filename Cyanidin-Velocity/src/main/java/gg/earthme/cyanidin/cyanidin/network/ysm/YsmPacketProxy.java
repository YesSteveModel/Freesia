package gg.earthme.cyanidin.cyanidin.network.ysm;

import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;


public interface YsmPacketProxy {
    ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData);

    ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData);

    void blockUntilProxyReady();
}
