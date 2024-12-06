package gg.earthme.cyanidin.common.communicating;

import gg.earthme.cyanidin.common.EntryPoint;
import gg.earthme.cyanidin.common.communicating.message.IMessage;
import gg.earthme.cyanidin.common.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
    private final int reconnectInterval;
    private volatile boolean isConnected = false;

    public NettySocketClient(InetSocketAddress masterAddress, Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator, int reconnectInterval) {
        this.masterAddress = masterAddress;
        this.handlerCreator = handlerCreator;
        this.reconnectInterval = reconnectInterval;
    }

    public void connect(){
        this.isConnecting = true;
        EntryPoint.LOGGER_INST.info("Connecting to master controller service.");
        try {
            this.clientChannelFuture = new Bootstrap()
                    .group(this.clientEventLoopGroup)
                    .channel(this.clientChannelType)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(@NotNull Channel channel) {
                            DefaultChannelPipelineLoader.loadDefaultHandlers(channel);
                            channel.pipeline().addLast(NettySocketClient.this.handlerCreator.apply(channel));
                        }
                    })
                    .connect(this.masterAddress.getHostName(), this.masterAddress.getPort())
                    .syncUninterruptibly();
            this.channel = this.clientChannelFuture.channel();
            this.isConnected = true;
        }catch (Exception e){
            EntryPoint.LOGGER_INST.error("Failed to connect master controller service!", e);
        }finally {
            this.isConnecting = false;
        }

        if (!this.isConnected){
            EntryPoint.LOGGER_INST.info("Trying to reconnect to the controller!");
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(this.reconnectInterval));

            if (!this.shouldDoNextReconnect()){
                return;
            }

            this.connect();
        }
    }

    protected boolean shouldDoNextReconnect() {
        return true;
    }

    public void onChannelInactive(){
        EntryPoint.LOGGER_INST.warn("Master controller has been disconnected!");
        this.isConnected = false;
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
