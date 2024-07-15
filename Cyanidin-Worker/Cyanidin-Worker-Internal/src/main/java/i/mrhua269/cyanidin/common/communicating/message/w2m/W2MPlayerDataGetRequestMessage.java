package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class W2MPlayerDataGetRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private int traceId;
    private UUID playerUUID;

    public W2MPlayerDataGetRequestMessage(){

    }

    public W2MPlayerDataGetRequestMessage(UUID playerUUID, int traceId){
        this.playerUUID = playerUUID;
        this.traceId = traceId;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        this.traceId = buffer.readInt();
        final long lsb = buffer.readLong();
        final long msb = buffer.readLong();

        this.playerUUID = new UUID(lsb, msb);
    }

    @Override
    public void process(NettyServerChannelHandlerLayer handler) {

    }
}
