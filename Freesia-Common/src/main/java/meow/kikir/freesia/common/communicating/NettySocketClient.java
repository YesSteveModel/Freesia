package meow.kikir.freesia.common.communicating;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.NettyUtils;
import meow.kikir.freesia.common.communicating.message.IMessage;
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
    private final InetSocketAddress serverAddress;
    private final Queue<IMessage<?>> packetFlushQueue = new ConcurrentLinkedQueue<>();
    private final Function<Channel, SimpleChannelInboundHandler<?>> handlerFactory;
    private final int reconnectInterval;

    private volatile Channel channel;
    private volatile boolean isConnected = false;
    private volatile boolean workerReady = false;

    public NettySocketClient(InetSocketAddress serverAddress, Function<Channel, SimpleChannelInboundHandler<?>> handlerFactory, int reconnectInterval) {
        this.serverAddress = serverAddress;
        this.handlerFactory = handlerFactory;
        this.reconnectInterval = reconnectInterval;
    }

    public void attachShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Channel ch = this.channel;

            if (ch != null) {
                ch.close();
            }

            this.clientEventLoopGroup.shutdownGracefully();
        }));
    }

    public void connect() {
        EntryPoint.LOGGER_INST.info("Connecting to master controller service.");
        try {
            new Bootstrap()
                    .group(this.clientEventLoopGroup)
                    .channel(this.clientChannelType)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(@NotNull Channel channel) {
                            ChannelPipelineLoader.loadDefaultHandlers(channel);
                            channel.pipeline().addLast(NettySocketClient.this.handlerFactory.apply(channel));
                        }
                    })
                    .connect(this.serverAddress)
                    .syncUninterruptibly();
            this.isConnected = true;
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to connect master controller service!", e);
        }

        if (!this.isConnected) {
            EntryPoint.LOGGER_INST.info("Trying to reconnect to the controller!");
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(this.reconnectInterval));

            if (!this.shouldDoNextReconnect()) {
                this.onReady(); // we need to break out that await loop
                return;
            }

            this.connect();
        }
    }

    protected boolean shouldDoNextReconnect() {
        return true;
    }

    public void onChannelInactive() {
        EntryPoint.LOGGER_INST.warn("Master controller has been disconnected!");
        this.isConnected = false;
    }

    public void onChannelActive(Channel channel) {
        this.channel = channel;

        this.flushMessageQueueIfNeeded();
    }

    private void flushMessageQueueIfNeeded() {
        IMessage<?> toSend;
        while ((toSend = this.packetFlushQueue.poll()) != null) {
            this.channel.writeAndFlush(toSend);
        }
    }

    public void resetReadyFlag() {
        this.workerReady = false;
    }

    public void onReady() {
        this.workerReady = true;
    }

    public boolean isReady() {
        return this.workerReady;
    }
}
