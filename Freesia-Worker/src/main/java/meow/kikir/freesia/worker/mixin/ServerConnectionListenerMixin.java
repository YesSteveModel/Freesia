package meow.kikir.freesia.worker.mixin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.timeout.ReadTimeoutHandler;
import meow.kikir.freesia.common.utils.DomainUtils;
import meow.kikir.freesia.worker.FreesiaWorkerConfig;
import meow.kikir.freesia.worker.utils.ServerConnectionListenerHijack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.LegacyQueryHandler;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;

import java.net.UnixDomainSocketAddress;
import java.util.List;
import java.util.function.Supplier;

@Mixin(ServerConnectionListener.class)
public class ServerConnectionListenerMixin implements ServerConnectionListenerHijack {
    @Shadow
    @Final
    private List<ChannelFuture> channels;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    public static Supplier<NioEventLoopGroup> SERVER_EVENT_GROUP;

    @Shadow
    @Final
    public static Supplier<EpollEventLoopGroup> SERVER_EPOLL_EVENT_GROUP;

    @Shadow
    @Final
    private List<Connection> connections;

    @Unique
    private DomainSocketAddress domainSocketAddress = null;

    @Unique
    @Override
    public void freesia$setUnixDomainSocketAddress(DomainSocketAddress udsAddress) {
        this.domainSocketAddress = udsAddress;
    }

    @Unique
    @Override
    public void freesia$startOnUDS() {
        if (this.domainSocketAddress == null) {
            throw new UnsupportedOperationException();
        }

        synchronized(this.channels) {
            Class<? extends ServerChannel> channelClass;
            EventLoopGroup eventLoopGroup;
            if (Epoll.isAvailable() && this.server.isEpollEnabled()) {
                channelClass = EpollServerSocketChannel.class;
                eventLoopGroup = SERVER_EPOLL_EVENT_GROUP.get();
                LOGGER.info("Using epoll channel type");

                if (FreesiaWorkerConfig.useUnixDomainSocket) {
                    DomainUtils.throwIfUDSIsUnavailable();

                    channelClass = EpollServerDomainSocketChannel.class;
                    LOGGER.info("Using unix domain socket");
                }
            } else {
                channelClass = NioServerSocketChannel.class;
                eventLoopGroup = SERVER_EVENT_GROUP.get();
                LOGGER.info("Using default channel type");
            }

            this.channels.add((new ServerBootstrap()).channel(channelClass).childHandler(new ChannelInitializer<>() {
                protected void initChannel(Channel channel) {
                    if (!FreesiaWorkerConfig.useUnixDomainSocket) {
                        try {
                            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException ignored) {
                        }
                    }

                    ChannelPipeline pipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                    if (server.repliesToStatus()) {
                        pipeline.addLast("legacy_query", new LegacyQueryHandler(server));
                    }

                    Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND, false, null);

                    final Connection newConnection = new Connection(PacketFlow.SERVERBOUND);

                    connections.add(newConnection);
                    newConnection.configurePacketHandler(pipeline);
                    newConnection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(server, newConnection));
                }
            }).group(eventLoopGroup).localAddress(this.domainSocketAddress).bind().syncUninterruptibly());
        }
    }
}
