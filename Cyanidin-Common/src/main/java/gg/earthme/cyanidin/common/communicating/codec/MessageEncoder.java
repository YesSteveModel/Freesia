package gg.earthme.cyanidin.common.communicating.codec;

import gg.earthme.cyanidin.common.communicating.message.IMessage;
import gg.earthme.cyanidin.common.communicating.BuiltinMessageRegitres;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.jetbrains.annotations.NotNull;

public class MessageEncoder extends MessageToByteEncoder<IMessage<?>> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, @NotNull IMessage<?> message, ByteBuf out) {
        final int packetId = BuiltinMessageRegitres.getMessageId((Class<IMessage<?>>) message.getClass());
        out.writeInt(packetId);
        message.writeMessageData(out);
    }
}
