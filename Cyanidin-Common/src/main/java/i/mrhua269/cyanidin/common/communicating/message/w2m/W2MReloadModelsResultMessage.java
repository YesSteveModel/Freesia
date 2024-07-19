package i.mrhua269.cyanidin.common.communicating.message.w2m;

import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

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
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeBoolean(this.succeed);
        buffer.writeBoolean(this.requester != null);
        buffer.writeLong(this.requester.getMostSignificantBits());
        buffer.writeLong(this.requester.getLeastSignificantBits());
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.succeed = buffer.readBoolean();
        if(buffer.readBoolean()){
            this.requester = new UUID(buffer.readLong(), buffer.readLong());
        }
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.onModelReloadResult(this.requester, this.succeed);
    }
}
