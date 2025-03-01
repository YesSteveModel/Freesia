package meow.kikir.freesia.common.communicating;

import io.netty.channel.Channel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import meow.kikir.freesia.common.communicating.codec.MessageDecoder;
import meow.kikir.freesia.common.communicating.codec.MessageEncoder;
import org.jetbrains.annotations.NotNull;

public class DefaultChannelPipelineLoader {

    public static void loadDefaultHandlers(@NotNull Channel channel) {
        channel.pipeline()
                .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                .addLast(new LengthFieldPrepender(4))
                .addLast(new MessageEncoder())
                .addLast(new MessageDecoder());
    }

}
