package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.NettyUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import java.net.InetSocketAddress;

public class NettySocketServer {
    private final InetSocketAddress bindAddress;
    private final EventLoopGroup masterLoopGroup = NettyUtils.eventLoopGroup();
    private final EventLoopGroup workerLoopGroup = NettyUtils.eventLoopGroup();
    private volatile ChannelFuture channelFuture;

    public NettySocketServer(InetSocketAddress bindAddress) {
        this.bindAddress = bindAddress;
    }

    public void bind(){
        this.channelFuture = new ServerBootstrap()
                .group(this.masterLoopGroup, this.workerLoopGroup)
                .channel(NettyUtils.serverChannelClass())
                .option(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {

                    }
                })
                .bind(this.bindAddress.getHostName(), this.bindAddress.getPort())
                .awaitUninterruptibly();
    }
}
