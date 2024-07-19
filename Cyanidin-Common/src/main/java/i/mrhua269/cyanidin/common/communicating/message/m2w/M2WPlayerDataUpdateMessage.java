package i.mrhua269.cyanidin.common.communicating.message.m2w;

import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class M2WPlayerDataUpdateMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private UUID target;
    private byte[] nbtData;

    public M2WPlayerDataUpdateMessage(UUID target, byte[] nbtData) {
        this.target = target;
        this.nbtData = nbtData;
    }

    public M2WPlayerDataUpdateMessage() {}

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeLong(this.target.getMostSignificantBits());
        buffer.writeLong(this.target.getLeastSignificantBits());
        buffer.writeBytes(this.nbtData);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.target = new UUID(buffer.readLong(), buffer.readLong());
        this.nbtData = new byte[buffer.readableBytes()];
        buffer.readBytes(this.nbtData);
    }

    @Override
    public void process(@NotNull NettyClientChannelHandlerLayer handler) {
        handler.updatePlayerData(this.target, this.nbtData);
    }
}
