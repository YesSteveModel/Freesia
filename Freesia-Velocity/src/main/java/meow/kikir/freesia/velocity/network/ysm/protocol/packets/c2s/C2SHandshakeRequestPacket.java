package meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.velocity.events.PlayerYsmHandshakeEvent;
import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class C2SHandshakeRequestPacket implements YsmPacket {
    private String clientYsmVersion;

    public C2SHandshakeRequestPacket() {
        this(YsmProtocolMetaFile.getYsmChannelPath().replace("_", "."));
    }

    public C2SHandshakeRequestPacket(String clientYsmVersion) {
        this.clientYsmVersion = clientYsmVersion;
    }

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeUtf(this.clientYsmVersion);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.clientYsmVersion = input.readUtf();
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        final Player player = handler.getBindPlayer();

        final ResultedEvent.GenericResult result = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerYsmHandshakeEvent(player, this.clientYsmVersion)).join().getResult();

        if (!result.isAllowed()) {
            return ProxyComputeResult.ofDrop();
        }

        Freesia.LOGGER.info("Player {} is connected to the backend with ysm version {}", player.getUsername(), clientYsmVersion);
        Freesia.mappersManager.onClientYsmHandshakePacketReply(player);
        return ProxyComputeResult.ofPass();
    }
}
