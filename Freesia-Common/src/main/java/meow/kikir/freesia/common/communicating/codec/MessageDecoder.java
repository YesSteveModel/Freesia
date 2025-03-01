package meow.kikir.freesia.common.communicating.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import meow.kikir.freesia.common.communicating.BuiltinMessageRegitres;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class MessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, @NotNull ByteBuf byteBuf, List<Object> list) throws Exception {
        final int packetId = byteBuf.readInt();
        final Supplier<? extends IMessage<?>> packetCreator = BuiltinMessageRegitres.getMessageCreator(packetId);

        try {
            final IMessage<?> wrapped = packetCreator.get();
            wrapped.readMessageData(byteBuf);
            list.add(wrapped);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
