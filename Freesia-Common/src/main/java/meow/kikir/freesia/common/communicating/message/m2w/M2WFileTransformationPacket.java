package meow.kikir.freesia.common.communicating.message.m2w;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.ClientChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class M2WFileTransformationPacket implements IMessage<ClientChannelHandlerBase> {
    private int traceId; // trace id for each file transferring
    private int ack; // current ack to reply
    private int tAck; // full ack count

    private Path target; // path file
    private byte[] data; // data chunk
    private int beginOffset; // offset in path file

    public M2WFileTransformationPacket() {}

    public M2WFileTransformationPacket(int traceId, int ack, int tAck, int beginOffset, Path target, byte[] data) {
        this.traceId = traceId;
        this.ack = ack;
        this.tAck = tAck;
        this.target = target;
        this.data = data;
        this.beginOffset = beginOffset;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeInt(this.ack);
        buffer.writeInt(this.tAck);
        buffer.writeInt(this.beginOffset);

        if (this.target != null) {
            final byte[] targetPathBytes = this.target.toString().getBytes(StandardCharsets.UTF_8);
            buffer.writeInt(targetPathBytes.length);
            buffer.writeBytes(targetPathBytes);
        }else {
            buffer.writeInt(0);
        }

        if (this.data != null) {
            buffer.writeInt(this.data.length);
            buffer.writeBytes(this.data);
        }else {
            buffer.writeInt(0);
        }
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        this.ack = buffer.readInt();
        this.tAck = buffer.readInt();
        this.beginOffset = buffer.readInt();

        final int targetPathLen = buffer.readInt();
        if (targetPathLen > 0) {
            final byte[] targetPathBytes = new byte[targetPathLen];
            buffer.readBytes(targetPathBytes);
            this.target = Path.of(new String(targetPathBytes, StandardCharsets.UTF_8));
        }

        final int dataLen = buffer.readInt();
        if (dataLen > 0) {
            this.data = new byte[dataLen];
            buffer.readBytes(this.data);
        }
    }

    @Override
    public void process(ClientChannelHandlerBase handler) {
        handler.handleFileTransformation(this.traceId, this.ack, this.tAck, this.beginOffset, this.target, this.data);
    }
}
