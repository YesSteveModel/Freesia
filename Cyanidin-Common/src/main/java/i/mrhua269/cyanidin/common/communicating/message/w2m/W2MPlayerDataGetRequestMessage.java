package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class W2MPlayerDataGetRequestMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private UUID playerUUID;

    public W2MPlayerDataGetRequestMessage(){

    }

    public W2MPlayerDataGetRequestMessage(UUID playerUUID){
        this.playerUUID = playerUUID;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        final long lsb = buffer.readLong();
        final long msb = buffer.readLong();

        this.playerUUID = new UUID(lsb, msb);
    }

    @Override
    public void process(NettyServerChannelHandlerLayer handler) {

    }
}
