package i.mrhua269.cyanidin.common.communicating.codec;

import i.mrhua269.cyanidin.common.communicating.BuiltinMessageRegitres;
import i.mrhua269.cyanidin.common.communicating.EnumSide;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class MessageDecoder extends ByteToMessageDecoder {
    private final EnumSide side;

    public MessageDecoder(EnumSide side) {
        this.side = side;
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        final int packetId = byteBuf.readInt();
        final Supplier<IMessage> packetCreator = this.side == EnumSide.S2C ? BuiltinMessageRegitres.getS2CMessageCreator(packetId) : BuiltinMessageRegitres.getC2SMessageCreator(packetId);

        try {
            final IMessage wrapped = packetCreator.get();
            wrapped.readMessageData(byteBuf);
            list.add(wrapped);
        }catch (Exception e){
            throw new IOException(e);
        }
    }
}
