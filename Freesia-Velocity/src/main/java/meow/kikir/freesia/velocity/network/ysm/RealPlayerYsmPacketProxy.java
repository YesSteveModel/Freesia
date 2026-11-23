package meow.kikir.freesia.velocity.network.ysm;

import com.velocitypowered.api.proxy.Player;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.events.PlayerKnownYsmPacketInputEvent;
import meow.kikir.freesia.velocity.events.PlayerKnownYsmPacketOutputEvent;
import meow.kikir.freesia.velocity.network.ysm.protocol.EnumPacketDirection;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacketCodec;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s.C2SHandshakeRequestPacket;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RealPlayerYsmPacketProxy extends YsmPacketProxyBase {

    public RealPlayerYsmPacketProxy(Player player) {
        super(player);
    }

    @Override
    public Set<Player> visiblePlayersTo(UUID beingWatched) {
        return Freesia.tracker.getVisiblePlayersTo(beingWatched);
    }


    @Override
    public ProxyComputeResult processS2C(Key key, ByteBuf copiedPacketData) {
        final SimpleFriendlyByteBuf mcBuffer = new SimpleFriendlyByteBuf(copiedPacketData);
        YsmPacket decoded = YsmPacketCodec.tryDecode(mcBuffer, EnumPacketDirection.S2C);

        if (decoded != null) {
            final PlayerKnownYsmPacketOutputEvent pktEvent = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerKnownYsmPacketOutputEvent(
                                decoded, ProxyComputeResult.ofPass(), this.player
            )).join();

            // dropped by plugins
            final ProxyComputeResult computeResult = pktEvent.getHandleResult();
            if (computeResult.result() == ProxyComputeResult.EnumResult.DROP) {
                return computeResult;
            }

            // modified
            if (computeResult.result() == ProxyComputeResult.EnumResult.MODIFY) {
                YsmPacket toReplace = pktEvent.getPacket();

                if (toReplace == null) {
                    Freesia.LOGGER.warn("Null ysm packet process result was set! Rejecting forward process.");
                    return ProxyComputeResult.ofDrop();
                }

                decoded = toReplace;
            }


            return decoded.handle(this.handler);
        }

        return ProxyComputeResult.ofPass();
    }

    @Override
    public ProxyComputeResult processC2S(Key key, ByteBuf copiedPacketData) {
        final SimpleFriendlyByteBuf mcBuffer = new SimpleFriendlyByteBuf(copiedPacketData);
        YsmPacket decoded = YsmPacketCodec.tryDecode(mcBuffer, EnumPacketDirection.C2S);

        if (decoded != null) {
            final PlayerKnownYsmPacketInputEvent pktEvent = Freesia.PROXY_SERVER.getEventManager().fire(new PlayerKnownYsmPacketInputEvent(
                    decoded, this.player, ProxyComputeResult.ofPass()
            )).join();

            // dropped by plugins
            final ProxyComputeResult computeResult = pktEvent.getHandleResult();
            if (computeResult.result() == ProxyComputeResult.EnumResult.DROP) {
                return computeResult;
            }

            // modified
            if (computeResult.result() == ProxyComputeResult.EnumResult.MODIFY) {
                YsmPacket toReplace = pktEvent.getPacket();

                if (toReplace == null) {
                    Freesia.LOGGER.warn("Null ysm packet process result was set! Rejecting forward process.");
                    return ProxyComputeResult.ofDrop();
                }

                decoded = toReplace;
            }


            return decoded.handle(this.handler);
        }

        return ProxyComputeResult.ofPass();
    }

    // TODO - Remove after fixed
    @Override
    public void onProxyReadyCallback() {
        if (this.player == null) {
            return;
        }

        if (!Freesia.mappersManager.isInitiallyHandshakeReplied(this.player)) {
            return;
        }

        final MapperConnectionHandler connectionHandler = this.handler;

        if (connectionHandler != null) {
            final C2SHandshakeRequestPacket spoof = new C2SHandshakeRequestPacket();
            final @NotNull SimpleFriendlyByteBuf encoded = YsmPacketCodec.encode(spoof);

            final byte[] encodedInBytes = new byte[encoded.readableBytes()];
            encoded.readBytes(encodedInBytes);

            connectionHandler.sendPacket(new ServerboundCustomPayloadPacket(MappersManager.YSM_CHANNEL_KEY_ADVENTURE, encodedInBytes));
        }
    }
}
