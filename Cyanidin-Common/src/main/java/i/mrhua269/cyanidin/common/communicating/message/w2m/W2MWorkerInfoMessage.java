package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class W2MWorkerInfoMessage implements IMessage<NettyServerChannelHandlerLayer>{
    private UUID workerUUID;
    private String workerName;

    public W2MWorkerInfoMessage() {}

    public W2MWorkerInfoMessage(UUID workerUUID, String workerName) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeLong(workerUUID.getMostSignificantBits());
        buffer.writeLong(workerUUID.getLeastSignificantBits());
        buffer.writeBytes(workerName.getBytes());
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        workerUUID = new UUID(buffer.readLong(), buffer.readLong());
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        workerName = new String(bytes);
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.updateWorkerInfo(this.workerUUID, this.workerName);
    }
}
