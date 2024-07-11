package i.mrhua269.cyanidin.common.communicating.message;

import io.netty.buffer.ByteBuf;

public interface IMessage {

    void writeMessageData(ByteBuf buffer);

    void readMessageData(ByteBuf buffer);

}
