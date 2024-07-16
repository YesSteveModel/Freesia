package i.mrhua269.cyanidin.common.communicating.handler;

import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.NettySocketClient;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public abstract class NettyClientChannelHandlerLayer extends SimpleChannelInboundHandler<IMessage<NettyClientChannelHandlerLayer>> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<NettyClientChannelHandlerLayer> msg) throws Exception {
        try {
            msg.process(this);
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.getClient().onChannelInactive();
    }

    public abstract NettySocketClient getClient();

    public abstract void onMasterPlayerDataResponse(int traceId, byte[] content);
}
