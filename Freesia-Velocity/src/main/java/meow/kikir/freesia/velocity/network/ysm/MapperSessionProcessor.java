package meow.kikir.freesia.velocity.network.ysm;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import java.util.concurrent.locks.LockSupport;

public class MapperSessionProcessor implements SessionListener {
    private final Player bindPlayer;
    private final YsmPacketProxy packetProxy;
    private final YsmMapperPayloadManager mapperPayloadManager;
    private volatile Session session;
    private volatile boolean kickMasterWhenDisconnect = true;

    public MapperSessionProcessor(Player bindPlayer, YsmPacketProxy packetProxy, YsmMapperPayloadManager mapperPayloadManager) {
        this.bindPlayer = bindPlayer;
        this.packetProxy = packetProxy;
        this.mapperPayloadManager = mapperPayloadManager;
    }

    public YsmPacketProxy getPacketProxy() {
        return this.packetProxy;
    }

    public Session getSession() {
        return this.session;
    }

    public void setKickMasterWhenDisconnect(boolean kickMasterWhenDisconnect) {
        this.kickMasterWhenDisconnect = kickMasterWhenDisconnect;
    }

    public void processPlayerPluginMessage(byte[] packetData) {
        final ProxyComputeResult result = this.packetProxy.processC2S(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, Unpooled.copiedBuffer(packetData));

        switch (result.result()) {
            case MODIFY -> {
                final ByteBuf finalData = result.data();

                finalData.resetReaderIndex();
                byte[] data = new byte[finalData.readableBytes()];
                finalData.readBytes(data);

                this.session.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, data));
            }

            case PASS ->
                    this.session.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, packetData));
        }
    }

    public Player getBindPlayer() {
        return this.bindPlayer;
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            Freesia.mapperManager.updateWorkerPlayerEntityId(this.bindPlayer, loginPacket.getEntityId());
            Freesia.mapperManager.onProxyLoggedin(this.bindPlayer, this, ((TcpClientSession) session));
        }

        if (packet instanceof ClientboundCustomPayloadPacket payloadPacket) {
            final Key channelKey = payloadPacket.getChannel();
            final byte[] packetData = payloadPacket.getData();

            if (channelKey.toString().equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE.toString())) {
                final ProxyComputeResult result = this.packetProxy.processS2C(channelKey, Unpooled.wrappedBuffer(packetData));

                switch (result.result()) {
                    case MODIFY -> {
                        final ByteBuf finalData = result.data();

                        finalData.resetReaderIndex();

                        this.packetProxy.sendPluginMessageToOwner(MinecraftChannelIdentifier.create(channelKey.namespace(), channelKey.value()), finalData);
                    }

                    case PASS ->
                            this.packetProxy.sendPluginMessageToOwner(MinecraftChannelIdentifier.create(channelKey.namespace(), channelKey.value()), packetData);
                }
            }
        }

        // Reply the fabric mod loader ping checks
        if (packet instanceof ClientboundPingPacket pingPacket) {
            session.send(new ServerboundPongPacket(pingPacket.getId()));
        }
    }

    @Override
    public void packetSending(PacketSendingEvent event) {

    }

    @Override
    public void packetSent(Session session, Packet packet) {

    }

    @Override
    public void packetError(PacketErrorEvent event) {

    }

    @Override
    public void connected(ConnectedEvent event) {
        this.session = event.getSession();
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {

    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        Freesia.LOGGER.info("Mapper session has disconnected for reason(non-deserialized): {}", event.getReason()); // Log disconnected
        if (event.getCause() != null) {
            Freesia.LOGGER.info("Mapper session has disconnected for throwable: {}", event.getCause().getLocalizedMessage()); // Log errors
        }
        this.mapperPayloadManager.onWorkerSessionDisconnect(this, this.kickMasterWhenDisconnect, event.getReason()); // Fire events
        this.session = null; //Set session to null to finalize the mapper connection
    }

    public void waitForDisconnected() {
        while (this.session != null) {
            Thread.onSpinWait(); // Spin wait instead of block waiting
        }
    }
}
