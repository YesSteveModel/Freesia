package meow.kikir.freesia.common.communicating.message.w2m;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.ServerChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

public class W2MFileTransformationAckPacket implements IMessage<ServerChannelHandlerBase> {
    private int traceId; // trace id for each file transferring
    private int ack; // current replied ack

    public W2MFileTransformationAckPacket() {}

    public W2MFileTransformationAckPacket(int traceId, int ack) {
        this.traceId = traceId;
        this.ack = ack;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeInt(this.ack);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        this.ack = buffer.readInt();
    }

    @Override
    public void process(ServerChannelHandlerBase handler) {
        handler.handleFileAck(this.traceId, this.ack);
    }
}
