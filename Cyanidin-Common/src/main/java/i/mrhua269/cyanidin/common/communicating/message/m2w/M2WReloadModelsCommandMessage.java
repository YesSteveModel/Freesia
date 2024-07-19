package i.mrhua269.cyanidin.common.communicating.message.m2w;

import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MReloadModelsResultMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class M2WReloadModelsCommandMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private UUID requester;

    public M2WReloadModelsCommandMessage(){}

    public M2WReloadModelsCommandMessage(UUID requester) {
        this.requester = requester;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeBoolean(this.requester != null);
        buffer.writeLong(requester.getMostSignificantBits());
        buffer.writeLong(requester.getLeastSignificantBits());
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        if (!buffer.readBoolean()){
            return;
        }
        this.requester = new UUID(buffer.readLong(), buffer.readLong());
    }

    @Override
    public void process(@NotNull NettyClientChannelHandlerLayer handler) {
        handler.callReloadModel().whenComplete((result, exception) -> handler.getClient().sendToMaster(new W2MReloadModelsResultMessage(this.requester, result)));
    }
}
