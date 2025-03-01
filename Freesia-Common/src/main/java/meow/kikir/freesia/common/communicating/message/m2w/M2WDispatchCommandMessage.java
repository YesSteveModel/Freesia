package meow.kikir.freesia.common.communicating.message.m2w;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.NettyClientChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.IMessage;
import meow.kikir.freesia.common.communicating.message.w2m.W2MCommandResultMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public class M2WDispatchCommandMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private int traceId;
    private String command;

    public M2WDispatchCommandMessage() {
    }

    public M2WDispatchCommandMessage(int traceId, String command) {
        this.traceId = traceId;
        this.command = command;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeCharSequence(this.command, StandardCharsets.UTF_8);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        this.command = buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString();
    }

    @Override
    public void process(NettyClientChannelHandlerLayer handler) {
        handler.dispatchCommand(this.command).whenComplete((result, command) -> handler.getClient().sendToMaster(new W2MCommandResultMessage(this.traceId, result)));
    }
}
