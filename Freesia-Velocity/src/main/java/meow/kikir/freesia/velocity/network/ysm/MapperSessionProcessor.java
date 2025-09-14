package meow.kikir.freesia.velocity.network.ysm;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.utils.PendingPacket;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.UUID;

public class MapperSessionProcessor implements SessionListener {
    private final Player bindPlayer;
    private final YsmPacketProxy packetProxy;
    private final YsmMapperPayloadManager mapperPayloadManager;

    // Callbacks for packet processing and tracker updates
    private final MultiThreadedQueue<PendingPacket> pendingYsmPacketsInbound = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<UUID> pendingTrackerUpdatesTo = new MultiThreadedQueue<>();

    // Controlled by the VarHandles following
    private volatile Session session;
    private boolean kickMasterWhenDisconnect = true;
    private boolean destroyed = false;

    private static final VarHandle KICK_MASTER_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class, "kickMasterWhenDisconnect", boolean.class);
    private static final VarHandle SESSION_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class, "session", Session.class);
    private static final VarHandle DESTROYED_HANDLE = ConcurrentUtil.getVarHandle(MapperSessionProcessor.class, "destroyed", boolean.class);

    public MapperSessionProcessor(Player bindPlayer, YsmPacketProxy packetProxy, YsmMapperPayloadManager mapperPayloadManager) {
        this.bindPlayer = bindPlayer;
        this.packetProxy = packetProxy;
        this.mapperPayloadManager = mapperPayloadManager;
    }

    protected boolean queueTrackerUpdate(UUID target) {
        return this.pendingTrackerUpdatesTo.offer(target);
    }

    protected void retireTrackerCallbacks(){
        UUID toSend;
        while ((toSend = this.pendingTrackerUpdatesTo.pollOrBlockAdds()) != null) {
            final Optional<Player> player = Freesia.PROXY_SERVER.getPlayer(toSend);

            if (player.isEmpty()) {
                continue;
            }

            final Player targetPlayer = player.get();

            this.packetProxy.sendEntityStateTo(targetPlayer);
        }
    }

    public boolean sendPacket(Packet packet) {
        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        if (sessionObject == null) {
            return false;
        }

        sessionObject.send(packet);
        return true;
    }

    protected YsmPacketProxy getPacketProxy() {
        return this.packetProxy;
    }

    protected void setKickMasterWhenDisconnect(boolean kickMasterWhenDisconnect) {
        KICK_MASTER_HANDLE.setVolatile(this, kickMasterWhenDisconnect);
    }

    protected void processPlayerPluginMessage(byte[] packetData) {
        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        // This case should never happen because player's ysm packet won't come in
        // until we forward the handshake packet from the worker side
        // And when the handshake packet is reached, the session was already set before
        // see YsmMapperPayloadManager#createMapperSession
        if (sessionObject == null) {
            throw new IllegalStateException("Processing plugin message on non-connected mapper");
        }

        final ProxyComputeResult result = this.packetProxy.processC2S(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, Unpooled.copiedBuffer(packetData));

        switch (result.result()) {
            case MODIFY -> {
                final ByteBuf finalData = result.data();

                finalData.resetReaderIndex();
                byte[] data = new byte[finalData.readableBytes()];
                finalData.readBytes(data);

                sessionObject.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, data));
            }

            case PASS ->
                    sessionObject.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, packetData));
        }
    }

    public Player getBindPlayer() {
        return this.bindPlayer;
    }

    protected void onBackendReady() {
        // Process incoming packets that we had not ready to process before
        PendingPacket pendingYsmPacket;
        while ((pendingYsmPacket = this.pendingYsmPacketsInbound.pollOrBlockAdds()) != null) { // Destroy(block add operations) the queue
            this.processInComingYsmPacket(pendingYsmPacket.channel(), pendingYsmPacket.data());
        }
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            // Notify entity update to notify the tracker update of the player
            Freesia.mapperManager.updateWorkerPlayerEntityId(this.bindPlayer, loginPacket.getEntityId());
        }

        if (packet instanceof ClientboundCustomPayloadPacket payloadPacket) {
            final Key channelKey = payloadPacket.getChannel();
            final byte[] packetData = payloadPacket.getData();

            // If the packet is of ysm
            if (channelKey.toString().equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE.toString())) {
                // Check if we are not ready for the backend side yet(We will block the add operations once the backend is ready for the player)
                final PendingPacket pendingPacket = new PendingPacket(channelKey, packetData);
                if (!this.pendingYsmPacketsInbound.offer(pendingPacket)) {
                    // Add is blocked, we'll process it directly
                    this.processInComingYsmPacket(channelKey, packetData);
                }
                // Otherwise, we push it into the callback queue
            }
        }

        // Reply the fabric mod loader ping checks
        if (packet instanceof ClientboundPingPacket pingPacket) {
            session.send(new ServerboundPongPacket(pingPacket.getId()));
        }
    }

    private void processInComingYsmPacket(Key channelKey, byte[] packetData) {
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
    }

    @Override
    public void disconnecting(DisconnectingEvent event) {

    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        this.detachFromManager(true, event);
    }

    // Sometimes the callback would not be called when we destroy an non-connected mapper,
    // so we separated the disconnect logics into here and manual call this in that cases
    protected void detachFromManager(boolean updateSession, @Nullable DisconnectedEvent disconnectedEvent) {
        Component reason = null;

        // Log disconnects if we disconnected it non-manually
        if (disconnectedEvent != null) {
            reason = disconnectedEvent.getReason();

            Freesia.LOGGER.info("Mapper session has disconnected for reason(non-deserialized): {}", reason); // Log disconnected

            final Throwable thr = disconnectedEvent.getCause();

            if (thr != null) {
                Freesia.LOGGER.error("Mapper session has disconnected for throwable", thr); // Log errors
            }
        }

        // Remove callback
        this.mapperPayloadManager.onWorkerSessionDisconnect(this, (boolean) KICK_MASTER_HANDLE.getVolatile(this), reason); // Fire events

        if (updateSession) {
            SESSION_HANDLE.setVolatile(this, null); //Set session to null to finalize the mapper connection
        }
    }

    protected void setSession(Session session) {
        SESSION_HANDLE.setVolatile(this, session);
    }

    public void destroyAndAwaitDisconnected() {
        // Prevent multiple disconnect calls
        if (!DESTROYED_HANDLE.compareAndSet(this, false, true)) {
            // Wait for fully disconnected
            this.waitForDisconnected();
            return;
        }

        final Session sessionObject = (Session) SESSION_HANDLE.getVolatile(this);

        // Destroy the session
        if (sessionObject != null) {
            sessionObject.disconnect("DESTROYED");
        }else {
            // Disconnecting a non initialized session
            // Manual call remove callbacks
            // Remember: HERE SHOULDN'T BE ANY RACE CONDITION
            this.detachFromManager(false, null);
        }

        // Wait for fully disconnected
        this.waitForDisconnected();
    }

    protected void waitForDisconnected() {
        // We will set the session to null after finishing all disconnect logics
        while (SESSION_HANDLE.getVolatile(this) != null) {
            Thread.onSpinWait(); // Spin wait instead of block waiting
        }
    }
}
