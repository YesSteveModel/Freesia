package i.mrhua269.cyanidin.common.communicating.handler;

import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NettyServerChannelHandlerLayer extends SimpleChannelInboundHandler<IMessage<NettyServerChannelHandlerLayer>> {
    private Channel channel;
    private final Queue<IMessage<NettyServerChannelHandlerLayer>> pendingPackets = new ConcurrentLinkedQueue<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.channel = ctx.channel();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<NettyServerChannelHandlerLayer> msg) throws Exception {
        try {
            msg.process(this);
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
        }
    }

    public void sendMessage(IMessage<NettyServerChannelHandlerLayer> packet){
        if (!this.channel.isOpen()){
            return;
        }

        if (this.channel == null){
            this.pendingPackets.offer(packet);
            return;
        }

        if (!this.channel.eventLoop().inEventLoop()){
            this.channel.eventLoop().execute(() -> this.sendMessage(packet));
            return;
        }

        if (!this.pendingPackets.isEmpty()){
            IMessage<NettyServerChannelHandlerLayer> pending;
            while ((pending = this.pendingPackets.poll()) != null){
                this.channel.writeAndFlush(pending);
            }
        }

        this.channel.writeAndFlush(packet);
    }
}
