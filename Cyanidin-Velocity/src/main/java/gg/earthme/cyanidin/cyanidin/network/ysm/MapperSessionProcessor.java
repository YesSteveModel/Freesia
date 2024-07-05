package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.LockSupport;

public class MapperSessionProcessor implements SessionListener{
    private final Player bindPlayer;
    private final YsmPacketProxy packetProxy;
    private final YsmMapperPayloadManager mapperPayloadManager;
    private volatile Session session;
    private volatile boolean readyForReceivingPackets = false;
    private volatile boolean kickMasterWhenDisconnect = true;
    private final Queue<Runnable> pendingPacketProcessQueue = new ConcurrentLinkedDeque<>();

    public MapperSessionProcessor(Player bindPlayer, YsmPacketProxy packetProxy, YsmMapperPayloadManager mapperPayloadManager) {
        this.bindPlayer = bindPlayer;
        this.packetProxy = packetProxy;
        this.mapperPayloadManager = mapperPayloadManager;
    }

    public YsmPacketProxy getPacketProxy(){
        return this.packetProxy;
    }

    public boolean isReadyForReceivingPackets(){
        return this.readyForReceivingPackets;
    }

    public Session getSession() {
        return this.session;
    }

    public void setKickMasterWhenDisconnect(boolean kickMasterWhenDisconnect){
        this.kickMasterWhenDisconnect = kickMasterWhenDisconnect;
    }

    public void onProxyReady(){
        Runnable callback;
        while ((callback = this.pendingPacketProcessQueue.poll()) != null){
            try {
                callback.run();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public void processPlayerPluginMessage(byte[] packetData){
        if (!this.isReadyForReceivingPackets()){
            this.pendingPacketProcessQueue.offer(() -> this.processPlayerPluginMessage(packetData));
            return;
        }

        final ByteBuf directPacketDataBuffer = Unpooled.copiedBuffer(Arrays.copyOf(packetData, packetData.length));
        if (this.packetProxy.processC2S(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, Unpooled.copiedBuffer(packetData), directPacketDataBuffer) == EnumPacketProxyResult.FORWARD){
            this.session.send(new ServerboundCustomPayloadPacket(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE, directPacketDataBuffer.array()));
        }
    }

    public Player getBindPlayer(){
        return this.bindPlayer;
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ClientboundLoginPacket){
            this.readyForReceivingPackets = true;
        }

        if (packet instanceof ClientboundCustomPayloadPacket payloadPacket){
            this.readyForReceivingPackets = true;

            final Key channelKey = payloadPacket.getChannel();
            final byte[] packetData = payloadPacket.getData();

            if (channelKey.toString().equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_ADVENTURE.toString())){
                final ByteBuf directPacketDataBuffer = Unpooled.directBuffer();

                try {
                    if (this.packetProxy.processS2C(channelKey, Unpooled.wrappedBuffer(packetData), directPacketDataBuffer) == EnumPacketProxyResult.FORWARD){
                        final byte[] processedData = new byte[directPacketDataBuffer.readableBytes()];
                        directPacketDataBuffer.readBytes(processedData);

                        this.bindPlayer.sendPluginMessage(MinecraftChannelIdentifier.create(channelKey.namespace(), channelKey.value()), processedData);
                    }
                }finally {
                    directPacketDataBuffer.release();
                }
            }
        }

        if (packet instanceof ClientboundPingPacket pingPacket){
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
        this.mapperPayloadManager.onWorkerSessionDisconnect(this, this.kickMasterWhenDisconnect);
        this.session = null;
    }

    public void waitForDisconnected(){
        while (true){
            final Session curr = this.session;

            if (curr == null){
                break;
            }

            if (!curr.isConnected()){
                break;
            }

            Thread.yield();
            LockSupport.parkNanos(1_000);
        }
    }
}
