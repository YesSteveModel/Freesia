package meow.kikir.freesia.common.communicating.message;

import io.netty.buffer.ByteBuf;

public interface IMessage<T> {

    void writeMessageData(ByteBuf buffer);

    void readMessageData(ByteBuf buffer);

    void process(T handler);
}
