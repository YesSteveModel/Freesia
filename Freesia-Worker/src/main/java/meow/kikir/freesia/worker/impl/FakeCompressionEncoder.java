package meow.kikir.freesia.worker.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.VarInt;

public class FakeCompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int i = msg.readableBytes();
        if (i > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + i + ", should be less than 8388608)");
        } else {
            VarInt.write(out, 0);
            out.writeBytes(msg);
        }
    }
}
