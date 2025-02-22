package meow.kikir.freesia.common.communicating.message.w2m;

import meow.kikir.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class W2MCommandResultMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private int traceId;
    private String result;

    public W2MCommandResultMessage(int traceId, String result) {
        this.traceId = traceId;
        this.result = result;
    }

    public W2MCommandResultMessage() {}

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeBoolean(this.result != null);
        buffer.writeCharSequence(this.result, StandardCharsets.UTF_8);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        if (!buffer.readBoolean()){
            return;
        }
        this.result = buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString();
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.onCommandDispatchResult(this.traceId, this.result);
    }
}
