package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.communicating.codec.MessageDecoder;
import i.mrhua269.cyanidin.common.communicating.codec.MessageEncoder;
import io.netty.channel.Channel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

public class DefaultChannelPipelineLoader {

    public static void loadDefaultHandlers(Channel channel, EnumSide side){
        channel.pipeline()
                .addLast(new LengthFieldPrepender(4))
                .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4))
                .addLast(new MessageEncoder())
                .addLast(new MessageDecoder(side));
    }

}
