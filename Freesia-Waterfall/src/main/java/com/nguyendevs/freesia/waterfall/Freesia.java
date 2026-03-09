package com.nguyendevs.freesia.waterfall;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.google.common.collect.Maps;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.NettySocketServer;
import com.nguyendevs.freesia.waterfall.command.ListYsmPlayersCommand;
import com.nguyendevs.freesia.waterfall.command.DispatchWorkerCommandCommand;
import com.nguyendevs.freesia.waterfall.i18n.I18NManager;
import com.nguyendevs.freesia.waterfall.network.backend.MasterServerMessageHandler;
import com.nguyendevs.freesia.waterfall.network.mc.FreesiaPlayerTracker;
import com.nguyendevs.freesia.waterfall.network.misc.VirtualPlayerManager;
import com.nguyendevs.freesia.waterfall.network.ysm.RealPlayerYsmPacketProxyImpl;
import com.nguyendevs.freesia.waterfall.network.ysm.VirtualYsmPacketProxyImpl;
import com.nguyendevs.freesia.waterfall.network.ysm.YsmMapperPayloadManager;
import com.nguyendevs.freesia.waterfall.storage.DefaultRealPlayerDataStorageManagerImpl;
import com.nguyendevs.freesia.waterfall.storage.DefaultVirtualPlayerDataStorageManagerImpl;
import com.nguyendevs.freesia.waterfall.storage.IDataStorageManager;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Event;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import java.util.logging.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class Freesia extends Plugin implements PacketListener, Listener {
    public static final FreesiaPlayerTracker tracker = new FreesiaPlayerTracker();
    public static final IDataStorageManager realPlayerDataStorageManager = new DefaultRealPlayerDataStorageManagerImpl();
    public static final IDataStorageManager virtualPlayerDataStorageManager = new DefaultVirtualPlayerDataStorageManagerImpl();
    public static final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();
    public static final Map<UUID, MasterServerMessageHandler> registedWorkers = Maps.newConcurrentMap();
    public static final I18NManager languageManager = new I18NManager();
    public static Freesia INSTANCE = null;
    public static Logger LOGGER = null;
    public static ProxyServer PROXY_SERVER = null;
    public static YsmClientKickingDetector kickChecker;
    public static YsmMapperPayloadManager mapperManager;
    public static NettySocketServer masterServer;

    private static void printLogo() {
        String RESET  = "\u001B[0m";
        String PINK   = "\u001B[38;5;213m";
        String PURPLE = "\u001B[38;5;99m";
        String GOLD   = "\u001B[38;5;220m";
        String AQUA   = "\u001B[38;5;117m";

        PROXY_SERVER.getLogger().info("");
        PROXY_SERVER.getLogger().info(PINK  + "   ███████╗██████╗ ███████╗███████╗███████╗██╗ █████╗    ██╗██╗" + RESET);
        PROXY_SERVER.getLogger().info(PINK  + "   ██╔════╝██╔══██╗██╔════╝██╔════╝██╔════╝██║██╔══██╗   ██║██║" + RESET);
        PROXY_SERVER.getLogger().info(PINK  + "   █████╗  ██████╔╝█████╗  █████╗  ███████╗██║███████║   ██║██║" + RESET);
        PROXY_SERVER.getLogger().info(PINK  + "   ██╔══╝  ██╔══██╗██╔══╝  ██╔══╝  ╚════██║██║██╔══██║   ██║██║" + RESET);
        PROXY_SERVER.getLogger().info(PURPLE +"   ██║     ██║  ██║███████╗███████╗███████║██║██║  ██║   ██║██║" + RESET);
        PROXY_SERVER.getLogger().info(PURPLE +"   ╚═╝     ╚═╝  ╚═╝╚══════╝╚══════╝╚══════╝╚═╝╚═╝  ╚═╝   ╚═╝╚═╝" + RESET);
        PROXY_SERVER.getLogger().info("");
        PROXY_SERVER.getLogger().info(PURPLE + "              Powered by YesSteveModel & All Contributors");
        PROXY_SERVER.getLogger().info(GOLD   + "              Version: " + BuildConstants.VERSION);
        PROXY_SERVER.getLogger().info(AQUA   + "              Development by NguyenDevs");
        PROXY_SERVER.getLogger().info("");
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        LOGGER = this.getLogger();
        PROXY_SERVER = this.getProxy();

        EntryPoint.initLogger(org.slf4j.LoggerFactory.getLogger(Freesia.class));

        printLogo();

        // Load config and i18n
        LOGGER.info("Loading config file and i18n");
        try {
            FreesiaConfig.init();
            languageManager.loadLanguageFile(FreesiaConfig.languageName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Registering events and packet listeners.");
        // Mapper (Core function)
        mapperManager = new YsmMapperPayloadManager(RealPlayerYsmPacketProxyImpl::new, VirtualYsmPacketProxyImpl::new);
        // Register mc packet listener
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        // Attach to ysm channel
        this.getProxy().registerChannel(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY); // Using string channel
        this.getProxy().getPluginManager().registerListener(this, this);

        // Init tracker
        tracker.init();
        tracker.addRealPlayerTrackerEventListener(mapperManager::onRealPlayerTrackerUpdate);
        tracker.addVirtualPlayerTrackerEventListener(mapperManager::onVirtualPlayerTrackerUpdate);

        // Init virtual player manager
        virtualPlayerManager.init();

        // Master controller service
        masterServer = new NettySocketServer(FreesiaConfig.masterServiceAddress, c -> new MasterServerMessageHandler());
        PROXY_SERVER.getScheduler().runAsync(this, () -> {
            try {
                LOGGER.info("Binding master service to " + FreesiaConfig.masterServiceAddress);
                masterServer.bind();
                LOGGER.info("Successfully bound master service.");
            } catch (Exception e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "Failed to bind master service!", e);
            }
        });

        LOGGER.info("Initiating client kicker.");

        // Client detection
        kickChecker = new YsmClientKickingDetector();
        kickChecker.bootstrap();

        LOGGER.info("Registering commands");
        this.getProxy().getPluginManager().registerCommand(this, new DispatchWorkerCommandCommand());
        this.getProxy().getPluginManager().registerCommand(this, new ListYsmPlayersCommand());
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        final ProxiedPlayer targetPlayer = event.getPlayer();

        getProxy().getScheduler().runAsync(this, () -> {
            mapperManager.onPlayerDisconnect(targetPlayer);
            kickChecker.onPlayerLeft(targetPlayer);
        });
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        final ProxiedPlayer targetPlayer = event.getPlayer();

        getProxy().getScheduler().runAsync(this, () -> {
            // Add to client kicker
            kickChecker.onPlayerJoin(targetPlayer);
        });
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        final ProxiedPlayer player = event.getPlayer();

        final boolean potentialDisconnected = mapperManager.disconnectAlreadyConnected(player);

        if (potentialDisconnected) {
            // Player switched server, do log
            getLogger().info("Player " + player.getName() + " has changed backend server. Reconnecting mapper session");
        } else {
            getLogger().info("Initiating mapper session for player " + player.getName());
        }

        // Re init after removed or init on first connected
        mapperManager.initMapperPacketProcessor(player);

        // Create or re-create mapper session
        getProxy().getScheduler().runAsync(this, () -> {
            mapperManager.autoCreateMapper(player);
        });
    }

    @EventHandler
    public void onChannelMsg(PluginMessageEvent event) {
        final String identifier = event.getTag();
        final byte[] data = event.getData();

        if (identifier.startsWith("yes_steve_model")) {
            if (FreesiaConfig.debug) {
                Freesia.LOGGER.info("[DEBUG] Received YSM packet on channel: " + identifier + " from "
                        + ((ProxiedPlayer) event.getSender()).getName());
            }
            if (identifier.equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY)
                    && (event.getSender() instanceof ProxiedPlayer)) {
                ProxiedPlayer player = (ProxiedPlayer) event.getSender();
                event.setCancelled(true);

                // TODO Need a packet rate limiter here?
                mapperManager.onPluginMessageIn(player, identifier, data);
            }
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        // Hook join packet for fetching player's entity id for the tracker
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            final WrapperPlayServerJoinGame playerSpawnPacket = new WrapperPlayServerJoinGame(event);
            final ProxiedPlayer target = (ProxiedPlayer) event.getPlayer();

            LOGGER.info("Entity id update for player " + target.getName() + " to " + playerSpawnPacket.getEntityId());

            // Update id and try notifying update once
            mapperManager.updateRealPlayerEntityId(target, playerSpawnPacket.getEntityId());

            // Finalize callbacks
            PROXY_SERVER.getScheduler().runAsync(this, () -> mapperManager.onBackendReady(target));
        }
    }
}

