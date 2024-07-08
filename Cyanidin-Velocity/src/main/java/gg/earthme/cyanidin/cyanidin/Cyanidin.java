package gg.earthme.cyanidin.cyanidin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.network.mc.CyanidinPlayerTracker;
import gg.earthme.cyanidin.cyanidin.network.ysm.DefaultYsmPacketProxyImpl;
import gg.earthme.cyanidin.cyanidin.network.ysm.YsmMapperPayloadManager;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Plugin(id = "cyanidin", name = "Cyanidin", version = "1.0.0", authors = {"Earthme"}, dependencies = @Dependency(id = "packetevents"))
public class Cyanidin implements PacketListener {
    @Inject
    private Logger logger;
    @Inject
    private ProxyServer proxyServer;

    public static Cyanidin INSTANCE = null;
    public static Logger LOGGER = null;
    public static ProxyServer PROXY_SERVER = null;

    public static final InetSocketAddress BACKEND_ADDRESS_MANAGEMENT = new InetSocketAddress("localhost", 19200); //TODO Config
    public static final InetSocketAddress BACKEND_ADDRESS_MC = new InetSocketAddress("localhost", 19199); //TODO Config
    public static final String YSM_PROTO_VERSION = "1_2_1";
    public static final MinecraftChannelIdentifier YSM_CHANNEL_MARKER = MinecraftChannelIdentifier.create("yes_steve_model", YSM_PROTO_VERSION);

    public static final YsmMapperPayloadManager mapperManager = new YsmMapperPayloadManager(DefaultYsmPacketProxyImpl::new);
    public static final CyanidinPlayerTracker tracker = new CyanidinPlayerTracker();

    @Subscribe
    public void onProxyStart(ProxyInitializeEvent event) {
        INSTANCE = this;
        LOGGER = this.logger;
        PROXY_SERVER = this.proxyServer;

        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        this.proxyServer.getChannelRegistrar().register(YSM_CHANNEL_MARKER);
        tracker.init();
        tracker.addTrackerEventListener(mapperManager::onPlayerTrackerUpdate);
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event){
        final Player targetPlayer = event.getPlayer();
        mapperManager.onPlayerDisconnect(targetPlayer);
    }

    @Subscribe
    public void onPlayerConnected(ServerConnectedEvent event){
        final Player targetPlayer = event.getPlayer();

        this.proxyServer.getScheduler().buildTask(this, () -> {
            if (!mapperManager.hasPlayer(targetPlayer)){
                this.logger.info("Initiating mapper session for player {}", targetPlayer.getUsername());
                mapperManager.onPlayerBackendConnected(targetPlayer);
                return;
            }

            logger.info("Player {} has changed backend server.Reconnecting mapper session", targetPlayer.getUsername());
            mapperManager.reconnectWorker(targetPlayer);
        }).delay(10, TimeUnit.MILLISECONDS).schedule();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event){
        mapperManager.updatePlayerEntityId(event.getPlayer(), -1);
    }

    @Subscribe
    public void onChannelMsg(PluginMessageEvent event){
        final ChannelIdentifier identifier = event.getIdentifier();
        final byte[] data = event.getData();

        if ((identifier instanceof MinecraftChannelIdentifier mineId) && (event.getSource() instanceof Player player)){
            mapperManager.onPluginMessageIn(player, mineId, data);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME){
            final WrapperPlayServerJoinGame playerSpawnPacket = new WrapperPlayServerJoinGame(event);
            final Player target = (Player) event.getPlayer();

            logger.info("Entity id update for player {} to {}", target.getUsername(), playerSpawnPacket.getEntityId());
            mapperManager.updatePlayerEntityId(target, playerSpawnPacket.getEntityId());
        }
    }
}
