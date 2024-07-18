package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class W2MReloadModelsResultMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private UUID requester;
    private boolean succeed;

    public W2MReloadModelsResultMessage(){}

    public W2MReloadModelsResultMessage(UUID requester, boolean isSucceed){
        this.requester = requester;
        this.succeed = isSucceed;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeBoolean(this.succeed);
        buffer.writeBoolean(this.requester != null);
        buffer.writeLong(this.requester.getMostSignificantBits());
        buffer.writeLong(this.requester.getLeastSignificantBits());
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        this.succeed = buffer.readBoolean();
        if(buffer.readBoolean()){
            this.requester = new UUID(buffer.readLong(), buffer.readLong());
        }
    }

    @Override
    public void process(NettyServerChannelHandlerLayer handler) {
        handler.onModelReloadResult(this.requester, this.succeed);
    }
}
