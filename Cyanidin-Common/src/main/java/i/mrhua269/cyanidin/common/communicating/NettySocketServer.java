package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.function.Function;

public class NettySocketServer {
    private final InetSocketAddress bindAddress;
    private final EventLoopGroup masterLoopGroup = NettyUtils.eventLoopGroup();
    private final EventLoopGroup workerLoopGroup = NettyUtils.eventLoopGroup();
    private volatile ChannelFuture channelFuture;
    private final Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator;

    public NettySocketServer(InetSocketAddress bindAddress, Function<Channel, SimpleChannelInboundHandler<?>> handlerCreator) {
        this.bindAddress = bindAddress;
        this.handlerCreator = handlerCreator;
    }

    public void bind(){
        this.channelFuture = new ServerBootstrap()
                .group(this.masterLoopGroup, this.workerLoopGroup)
                .channel(NettyUtils.serverChannelClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(@NotNull Channel channel) {
                        DefaultChannelPipelineLoader.loadDefaultHandlers(channel);
                        channel.pipeline().addLast(NettySocketServer.this.handlerCreator.apply(channel));
                    }
                })
                .bind(this.bindAddress.getHostName(), this.bindAddress.getPort())
                .awaitUninterruptibly();
    }
}
