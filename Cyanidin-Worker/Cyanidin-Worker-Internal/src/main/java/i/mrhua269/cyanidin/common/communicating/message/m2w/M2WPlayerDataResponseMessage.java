package i.mrhua269.cyanidin.common.communicating.message.m2w;

import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class M2WPlayerDataResponseMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private int traceId;
    private String base64Content;

    public M2WPlayerDataResponseMessage(){

    }

    public M2WPlayerDataResponseMessage(String base64Content, int traceId){
        this.base64Content = base64Content;
        this.traceId = traceId;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeInt(this.traceId);
        buffer.writeBoolean(this.base64Content != null);

        if (this.base64Content == null){
            return;
        }

        final byte[] data = this.base64Content.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(data.length);
        buffer.writeBytes(data);
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        this.traceId = buffer.readInt();
        if (!buffer.readBoolean()){
            this.base64Content = null;
            return;
        }

        final byte[] data = new byte[buffer.readInt()];
        buffer.readBytes(data);
        this.base64Content = new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public void process(NettyClientChannelHandlerLayer handler) {
        handler.onMasterPlayerDataResponse(this.traceId, this.base64Content);
    }
}
