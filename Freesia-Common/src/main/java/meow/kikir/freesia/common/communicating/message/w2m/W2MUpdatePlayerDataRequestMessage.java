package meow.kikir.freesia.common.communicating.message.w2m;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class W2MUpdatePlayerDataRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private byte[] content;
    private UUID playerUUID;

    public W2MUpdatePlayerDataRequestMessage() {
    }

    public W2MUpdatePlayerDataRequestMessage(UUID playerUUID, byte[] content) {
        this.playerUUID = playerUUID;
        this.content = content;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
        buffer.writeBytes(this.content);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        final long lsb = buffer.readLong();
        final long msb = buffer.readLong();
        this.content = new byte[buffer.readableBytes()];
        buffer.readBytes(this.content);

        this.playerUUID = new UUID(msb, lsb);
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.savePlayerData(this.playerUUID, this.content);
    }
}
