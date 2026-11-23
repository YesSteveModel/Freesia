package meow.kikir.freesia.common.communicating.message.m2w;

import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.handler.ClientChannelHandlerBase;
import meow.kikir.freesia.common.communicating.message.IMessage;

public class M2WReloadModelsCallCommand implements IMessage<ClientChannelHandlerBase> {
    @Override
    public void writeMessageData(ByteBuf buffer) {

    }

    @Override
    public void readMessageData(ByteBuf buffer) {

    }

    @Override
    public void process(ClientChannelHandlerBase handler) {
        EntryPoint.LOGGER_INST.info("Received model reload command from master controller.");

        handler.callYsmModelReload();
    }
}
