package meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c;

import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class S2CHandshakeConfirmedPacket implements YsmPacket {
    private String serverYsmVersion;

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeUtf(this.serverYsmVersion);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.serverYsmVersion = input.readUtf();
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        Freesia.LOGGER.info("Replying ysm client with server version {}.", this.serverYsmVersion);

        return ProxyComputeResult.ofPass();
    }
}
