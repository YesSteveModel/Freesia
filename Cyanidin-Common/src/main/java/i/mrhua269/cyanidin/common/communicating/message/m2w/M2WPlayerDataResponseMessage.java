package i.mrhua269.cyanidin.common.communicating.message.m2w;

import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class M2WPlayerDataResponseMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private int traceId;
    private byte[] content;

    public M2WPlayerDataResponseMessage(){

    }

    public M2WPlayerDataResponseMessage(byte[] content, int traceId){
        this.content = content;
        this.traceId = traceId;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeBoolean(this.content != null);

        if (this.content == null){
            return;
        }

        buffer.writeInt(this.content.length);
        buffer.writeBytes(this.content);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        this.traceId = buffer.readInt();
        if (!buffer.readBoolean()){
            this.content = null;
            return;
        }

        this.content = new byte[buffer.readInt()];
        buffer.readBytes(this.content);
    }

    @Override
    public void process(NettyClientChannelHandlerLayer handler) {
        handler.onMasterPlayerDataResponse(this.traceId, this.content);
    }
}
