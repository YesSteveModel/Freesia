package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class W2MUpdatePlayerDataRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private String nbtJson;
    private UUID playerUUID;

    public W2MUpdatePlayerDataRequestMessage(){}

    public W2MUpdatePlayerDataRequestMessage(UUID playerUUID, String nbtJson){
        this.playerUUID = playerUUID;
        this.nbtJson = nbtJson;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
        buffer.writeBytes(this.nbtJson.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        final long lsb = buffer.readLong();
        final long msb = buffer.readLong();
        final byte[] nbtJsonData = new byte[buffer.readableBytes()];
        buffer.readBytes(nbtJsonData);

        this.playerUUID = new UUID(lsb, msb);
        this.nbtJson = new String(nbtJsonData, StandardCharsets.UTF_8);
    }

    @Override
    public void process(NettyServerChannelHandlerLayer handler) {

    }
}
