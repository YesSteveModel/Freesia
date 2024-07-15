package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.EntryPoint;
import i.mrhua269.cyanidin.common.NettyUtils;
import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class NettySocketClient {
    private final EventLoopGroup clientEventLoopGroup = NettyUtils.eventLoopGroup();
    private final Class<? extends Channel> clientChannelType = NettyUtils.channelClass();
    private final InetSocketAddress masterAddress;
    private volatile ChannelFuture clientChannelFuture;
    private volatile Channel channel;
    private volatile boolean isConnecting = false;
    private final Queue<IMessage<?>> packetFlushQueue = new ConcurrentLinkedQueue<>();
    private final Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator;

    public NettySocketClient(InetSocketAddress masterAddress, Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator) {
        this.masterAddress = masterAddress;
        this.handlerCreator = handlerCreator;
    }

    public void connect(){
        this.isConnecting = true;
        EntryPoint.LOGGER_INST.info("Connecting to master controller service.");
        try {
            this.clientChannelFuture = new Bootstrap()
                    .group(this.clientEventLoopGroup)
                    .channel(this.clientChannelType)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            DefaultChannelPipelineLoader.loadDefaultHandlers(channel, EnumSide.S2C);
                            channel.pipeline().addLast(NettySocketClient.this.handlerCreator.apply(channel));
                        }
                    })
                    .connect(this.masterAddress.getHostName(), this.masterAddress.getPort())
                    .syncUninterruptibly();
            this.channel = this.clientChannelFuture.channel();
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to connect master controller service!");
            throw new RuntimeException(e);
        }finally {
            this.isConnecting = false;
        }
    }

    public void sendToMaster(IMessage<?> message){
        if (this.channel == null && !this.isConnecting){
            throw new IllegalStateException("Not connected");
        }

        if (!this.channel.isOpen()){
            if (this.isConnecting){
                this.packetFlushQueue.offer(message);
                return;
            }

            throw new IllegalStateException("Not connected");
        }

        if (!this.channel.eventLoop().inEventLoop()){
            this.channel.eventLoop().execute(() -> this.sendToMaster(message));
            return;
        }

        if (!this.packetFlushQueue.isEmpty()){
            IMessage<?> toSend;
            while ((toSend = this.packetFlushQueue.poll()) != null){
                this.channel.writeAndFlush(toSend);
            }
        }

        this.channel.writeAndFlush(message);
    }
}
