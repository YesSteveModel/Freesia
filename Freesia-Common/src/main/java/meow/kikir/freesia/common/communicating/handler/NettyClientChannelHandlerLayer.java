package meow.kikir.freesia.common.communicating.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketClient;
import meow.kikir.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class NettyClientChannelHandlerLayer extends SimpleChannelInboundHandler<IMessage<NettyClientChannelHandlerLayer>> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage<NettyClientChannelHandlerLayer> msg) {
        try {
            msg.process(this);
        } catch (Exception e) {
            EntryPoint.LOGGER_INST.error("Failed to process packet! ", e);
        }
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        this.getClient().onChannelInactive();
    }

    public abstract NettySocketClient getClient();

    public abstract void onMasterPlayerDataResponse(int traceId, byte[] content);

    public abstract CompletableFuture<String> dispatchCommand(String command);
}
