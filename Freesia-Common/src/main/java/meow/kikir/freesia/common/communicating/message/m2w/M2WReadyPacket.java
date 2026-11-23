package meow.kikir.freesia.common.communicating.message.m2w;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.ClientChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

public class M2WReadyPacket implements IMessage<ClientChannelHandlerBase> {
    @Override
    public void writeMessageData(ByteBuf buffer) {

    }

    @Override
    public void readMessageData(ByteBuf buffer) {

    }

    @Override
    public void process(@NotNull ClientChannelHandlerBase handler) {
        handler.handleReadyNotification();
    }
}
