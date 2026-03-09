package com.nguyendevs.freesia.common.communicating.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import com.nguyendevs.freesia.common.communicating.BuiltinMessageRegitres;
import com.nguyendevs.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

public class MessageEncoder extends MessageToByteEncoder<IMessage<?>> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, @NotNull IMessage<?> message, ByteBuf out) {
        final int packetId = BuiltinMessageRegitres.getMessageId((Class<IMessage<?>>) message.getClass());
        out.writeInt(packetId);
        message.writeMessageData(out);
    }
}

