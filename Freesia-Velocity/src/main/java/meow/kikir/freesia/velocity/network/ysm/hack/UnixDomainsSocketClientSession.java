package meow.kikir.freesia.velocity.network.ysm.hack;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.codec.PacketCodecHelper;
import org.geysermc.mcprotocollib.network.packet.PacketProtocol;
import org.geysermc.mcprotocollib.network.tcp.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UnixDomainsSocketClientSession extends TcpSession {
    private static EventLoopGroup EVENT_LOOP_GROUP;

    /**
     * See {@link EventLoopGroup#shutdownGracefully(long, long, TimeUnit)}
     */
    private static final int SHUTDOWN_QUIET_PERIOD_MS = 100;
    private static final int SHUTDOWN_TIMEOUT_MS = 500;

    private final PacketCodecHelper codecHelper;

    public UnixDomainsSocketClientSession(String host, PacketProtocol protocol) {
        super(host, 0, protocol);
        this.codecHelper = protocol.createHelper();
    }

    @Override
    public void connect(boolean wait, boolean transferring) {
        if (this.disconnected) {
            throw new IllegalStateException("Session has already been disconnected.");
        }

        synchronized (UnixDomainsSocketClientSession.class) {
            if (EVENT_LOOP_GROUP == null) {
                EVENT_LOOP_GROUP = new EpollEventLoopGroup();

                Runtime.getRuntime().addShutdownHook(new Thread(
                        () -> EVENT_LOOP_GROUP.shutdownGracefully(SHUTDOWN_QUIET_PERIOD_MS, SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)));
            }
        }

        final Bootstrap bootstrap = new Bootstrap()
                .channel(EpollDomainSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getFlag(BuiltinFlags.CLIENT_CONNECT_TIMEOUT, 30) * 1000)
                .group(EVENT_LOOP_GROUP)
                .remoteAddress(new DomainSocketAddress(this.host))
                .handler(new ChannelInitializer<>() {
                    @Override
                    public void initChannel(@NonNull Channel channel) {
                        PacketProtocol protocol = getPacketProtocol();
                        protocol.newClientSession(UnixDomainsSocketClientSession.this, transferring);

                        ChannelPipeline pipeline = channel.pipeline();

                        pipeline.addLast("read-timeout", new ReadTimeoutHandler(getFlag(BuiltinFlags.READ_TIMEOUT, 30)));
                        pipeline.addLast("write-timeout", new WriteTimeoutHandler(getFlag(BuiltinFlags.WRITE_TIMEOUT, 0)));

                        pipeline.addLast("encryption", new TcpPacketEncryptor());
                        pipeline.addLast("sizer", new TcpPacketSizer(protocol.getPacketHeader(), getCodecHelper()));
                        pipeline.addLast("compression", new TcpPacketCompression(getCodecHelper()));

                        pipeline.addLast("flow-control", new TcpFlowControlHandler());
                        pipeline.addLast("codec", new TcpPacketCodec(UnixDomainsSocketClientSession.this, true));
                        pipeline.addLast("flush-handler", new FlushHandler());
                        pipeline.addLast("manager", UnixDomainsSocketClientSession.this);
                    }
                });

        CompletableFuture<Void> handleFuture = new CompletableFuture<>();
        bootstrap.connect().addListener((futureListener) -> {
            if (!futureListener.isSuccess()) {
                exceptionCaught(null, futureListener.cause());
            }

            handleFuture.complete(null);
        });

        if (wait) {
            handleFuture.join();
        }
    }

    @Override
    public PacketCodecHelper getCodecHelper() {
        return this.codecHelper;
    }
}
