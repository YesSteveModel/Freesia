package gg.earthme.cyanidin.cyanidin.network.ysm;

import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;


public interface YsmPacketProxy {
    EnumPacketProxyResult processS2C(Key channelKey, ByteBuf copiedPacketData, ByteBuf direct);

    EnumPacketProxyResult processC2S(Key channelKey, ByteBuf copiedPacketData, ByteBuf direct);

    void blockUntilProxyReady();
}
