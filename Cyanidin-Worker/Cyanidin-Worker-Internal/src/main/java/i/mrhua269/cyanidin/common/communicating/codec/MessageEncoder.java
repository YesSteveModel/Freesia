package i.mrhua269.cyanidin.common.communicating.codec;

import i.mrhua269.cyanidin.common.communicating.BuiltinMessageRegitres;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MessageEncoder extends MessageToByteEncoder<IMessage> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, IMessage message, ByteBuf out) {
        final int packetId = BuiltinMessageRegitres.getMessageId(message.getClass());
        out.writeInt(packetId);
        message.writeMessageData(out);
    }
}
