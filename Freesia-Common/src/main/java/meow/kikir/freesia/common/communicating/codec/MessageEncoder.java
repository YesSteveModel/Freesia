package meow.kikir.freesia.common.communicating.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import meow.kikir.freesia.common.communicating.BuiltinMessageRegistries;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

public class MessageEncoder extends MessageToByteEncoder<IMessage<?>> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, @NotNull IMessage<?> message, ByteBuf out) {
        final int packetId = BuiltinMessageRegistries.getMessageId((Class<IMessage<?>>) message.getClass());
        out.writeInt(packetId);
        message.writeMessageData(out);
    }
}
