package i.mrhua269.cyanidin.common.communicating.message.m2w;

import i.mrhua269.cyanidin.common.communicating.handler.NettyClientChannelHandlerLayer;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class M2WPlayerDataResponseMessage implements IMessage<NettyClientChannelHandlerLayer> {
    private String nbtJson;

    public M2WPlayerDataResponseMessage(){

    }

    public M2WPlayerDataResponseMessage(String nbtJson){
        this.nbtJson = nbtJson;
    }

    @Override
    public void writeMessageData(ByteBuf buffer) {
        buffer.writeBoolean(this.nbtJson != null);
        final byte[] data = this.nbtJson.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(data.length);
        buffer.writeBytes(data);
    }

    @Override
    public void readMessageData(ByteBuf buffer) {
        if (!buffer.readBoolean()){
            return;
        }

        final byte[] data = new byte[buffer.readInt()];
        buffer.readBytes(data);
        this.nbtJson = new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public void process(NettyClientChannelHandlerLayer handler) {

    }
}
