package meow.kikir.freesia.common.communicating.message.w2m;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.IMessage;
import meow.kikir.freesia.common.communicating.message.m2w.M2WPlayerDataResponseMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class W2MPlayerDataGetRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private int traceId;
    private UUID playerUUID;

    public W2MPlayerDataGetRequestMessage() {

    }

    public W2MPlayerDataGetRequestMessage(UUID playerUUID, int traceId) {
        this.playerUUID = playerUUID;
        this.traceId = traceId;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        final long lsb = buffer.readLong();
        final long msb = buffer.readLong();

        this.playerUUID = new UUID(msb, lsb);
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.readPlayerData(this.playerUUID).whenComplete((result, error) -> handler.sendMessage(new M2WPlayerDataResponseMessage(result, this.traceId)));
    }
}
