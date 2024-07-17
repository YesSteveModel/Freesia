package i.mrhua269.cyanidin.common.communicating.handler;

import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class NettyServerChannelHandlerLayer extends SimpleChannelInboundHandler<IMessage<NettyServerChannelHandlerLayer>> {
    private Channel channel;
    private final Queue<IMessage<NettyClientChannelHandlerLayer>> pendingPackets = new ConcurrentLinkedQueue<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.channel = ctx.channel();
        EntryPoint.LOGGER_INST.info("Worker connected {}", this.channel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<NettyServerChannelHandlerLayer> msg) throws Exception {
        try {
            msg.process(this);
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
        }
    }

    public void sendMessage(IMessage<NettyClientChannelHandlerLayer> packet){
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
            IMessage<NettyClientChannelHandlerLayer> pending;
            while ((pending = this.pendingPackets.poll()) != null){
                this.channel.writeAndFlush(pending);
            }
        }

        this.channel.writeAndFlush(packet);
    }

    public abstract CompletableFuture<byte[]> readPlayerData(UUID playerUUID);

    public abstract CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content);

    public abstract void onModelReloadResult(UUID requester, boolean succeed);
}
