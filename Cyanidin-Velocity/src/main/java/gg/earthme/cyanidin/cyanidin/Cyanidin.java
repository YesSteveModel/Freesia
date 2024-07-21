package gg.earthme.cyanidin.cyanidin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.google.common.collect.Maps;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.command.WorkerCommandCommand;
import gg.earthme.cyanidin.cyanidin.datastorage.DefaultDataStorageManagerImpl;
import gg.earthme.cyanidin.cyanidin.datastorage.IDataStorageManager;
import gg.earthme.cyanidin.cyanidin.i18n.I18NManager;
import gg.earthme.cyanidin.cyanidin.network.backend.MasterServerMessageHandler;
import gg.earthme.cyanidin.cyanidin.network.mc.CyanidinPlayerTracker;
import gg.earthme.cyanidin.cyanidin.network.ysm.DefaultYsmPacketProxyImpl;
import gg.earthme.cyanidin.cyanidin.network.ysm.YsmMapperPayloadManager;
import gg.earthme.cyanidin.common.EntryPoint;
import gg.earthme.cyanidin.common.communicating.NettySocketServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "cyanidin", name = "Cyanidin", version = BuildConstants.VERSION, authors = {"Earthme"}, dependencies = @Dependency(id = "packetevents"))
public class Cyanidin implements PacketListener {
    @Inject
    private Logger logger;
    @Inject
    private ProxyServer proxyServer;

    public static Cyanidin INSTANCE = null;
    public static Logger LOGGER = null;
    public static ProxyServer PROXY_SERVER = null;

    public static final YsmMapperPayloadManager mapperManager = new YsmMapperPayloadManager(DefaultYsmPacketProxyImpl::new);
    public static final CyanidinPlayerTracker tracker = new CyanidinPlayerTracker();
    public static final IDataStorageManager dataStorageManager = new DefaultDataStorageManagerImpl();
    public static final Map<UUID, MasterServerMessageHandler> registedWorkers = Maps.newConcurrentMap();
    public static final I18NManager languageManager = new I18NManager();
    public static NettySocketServer masterServer;

    private static void printLogo(){
        LOGGER.info("------------------------------------------------------------------------------");
        LOGGER.info("   █████████                                   ███      █████  ███            ");
        LOGGER.info("  ███░░░░░███                                 ░░░      ░░███  ░░░             ");
        LOGGER.info(" ███     ░░░  █████ ████  ██████   ████████   ████   ███████  ████  ████████  ");
        LOGGER.info("░███         ░░███ ░███  ░░░░░███ ░░███░░███ ░░███  ███░░███ ░░███ ░░███░░███ ");
        LOGGER.info("░███          ░███ ░███   ███████  ░███ ░███  ░███ ░███ ░███  ░███  ░███ ░███ ");
        LOGGER.info("░░███     ███ ░███ ░███  ███░░███  ░███ ░███  ░███ ░███ ░███  ░███  ░███ ░███ ");
        LOGGER.info(" ░░█████████  ░░███████ ░░████████ ████ █████ █████░░████████ █████ ████ █████");
        LOGGER.info("  ░░░░░░░░░    ░░░░░███  ░░░░░░░░ ░░░░ ░░░░░ ░░░░░  ░░░░░░░░ ░░░░░ ░░░░ ░░░░░ ");
        LOGGER.info("               ███ ░███                                                       ");
        LOGGER.info("              ░░██████                                                        ");
        LOGGER.info("                    Powered by CyanidinMC, Version: {}", BuildConstants.VERSION);
        LOGGER.info("------------------------------------------------------------------------------");
    }

    @Subscribe
    public void onProxyStart(ProxyInitializeEvent event) {
        INSTANCE = this;
        LOGGER = this.logger;
        PROXY_SERVER = this.proxyServer;
        EntryPoint.initLogger(this.logger);

        printLogo();

        try {
            CyanidinConfig.init();
            languageManager.loadLanguageFile(CyanidinConfig.languageName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Registering events and packet listeners.");
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        this.proxyServer.getChannelRegistrar().register(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY);
        tracker.init();
        tracker.addTrackerEventListener(mapperManager::onPlayerTrackerUpdate);

        masterServer = new NettySocketServer(CyanidinConfig.masterServiceAddress, c -> new MasterServerMessageHandler());
        masterServer.bind();

        WorkerCommandCommand.register();
    }

    @Subscribe
    public void onPlayerDisconnect(@NotNull DisconnectEvent event){
        final Player targetPlayer = event.getPlayer();
        mapperManager.onPlayerDisconnect(targetPlayer);
    }

    @Subscribe
    public void onPlayerConnected(@NotNull ServerConnectedEvent event){
        final Player targetPlayer = event.getPlayer();

        mapperManager.onPlayerConnected(targetPlayer);
        this.proxyServer.getScheduler().buildTask(this, () -> {
            if (!mapperManager.hasPlayer(targetPlayer)){
                this.logger.info("Initiating mapper session for player {}", targetPlayer.getUsername());
                mapperManager.firstCreateMapper(targetPlayer);
                return;
            }

            logger.info("Player {} has changed backend server.Reconnecting mapper session", targetPlayer.getUsername());
            mapperManager.reconnectWorker(targetPlayer);
        }).delay(10, TimeUnit.MILLISECONDS).schedule();
    }

    @Subscribe
    public void onServerPreConnect(@NotNull ServerPreConnectEvent event){
        mapperManager.updateServerPlayerEntityId(event.getPlayer(), -1);
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event){
        final ChannelIdentifier identifier = event.getIdentifier();
        final byte[] data = event.getData();

        if ((identifier instanceof MinecraftChannelIdentifier mineId) && (event.getSource() instanceof Player player)){
            event.setResult(PluginMessageEvent.ForwardResult.handled());

            mapperManager.onPluginMessageIn(player, mineId, data);
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME){
            final WrapperPlayServerJoinGame playerSpawnPacket = new WrapperPlayServerJoinGame(event);
            final Player target = (Player) event.getPlayer();

            logger.info("Entity id update for player {} to {}", target.getUsername(), playerSpawnPacket.getEntityId());
            mapperManager.updateServerPlayerEntityId(target, playerSpawnPacket.getEntityId());
        }
    }
}
