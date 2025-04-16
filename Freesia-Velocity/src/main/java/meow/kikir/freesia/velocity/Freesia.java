package meow.kikir.freesia.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.google.common.collect.Maps;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketServer;
import meow.kikir.freesia.velocity.command.ListYsmPlayersCommand;
import meow.kikir.freesia.velocity.command.DispatchWorkerCommandCommand;
import meow.kikir.freesia.velocity.i18n.I18NManager;
import meow.kikir.freesia.velocity.network.backend.MasterServerMessageHandler;
import meow.kikir.freesia.velocity.network.mc.FreesiaPlayerTracker;
import meow.kikir.freesia.velocity.network.misc.VirtualPlayerManager;
import meow.kikir.freesia.velocity.network.ysm.DefaultYsmPacketProxyImpl;
import meow.kikir.freesia.velocity.network.ysm.VirtualYsmPacketProxyImpl;
import meow.kikir.freesia.velocity.network.ysm.YsmMapperPayloadManager;
import meow.kikir.freesia.velocity.storage.DefaultRealPlayerDataStorageManagerImpl;
import meow.kikir.freesia.velocity.storage.DefaultVirtualPlayerDataStorageManagerImpl;
import meow.kikir.freesia.velocity.storage.IDataStorageManager;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "freesia", name = "Freesia", version = "2.3.2-universal", authors = {"Earthme", "HappyRespawnanchor", "xiaozhangup"}, dependencies = @Dependency(id = "packetevents"))
public class Freesia implements PacketListener {
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

    @Inject
    private Logger logger;
    @Inject
    private ProxyServer proxyServer;

    private static void printLogo() {
        PROXY_SERVER.sendMessage(Component.text("----------------------------------------------------------------"));
        PROXY_SERVER.sendMessage(Component.text("    ______                         _       "));
        PROXY_SERVER.sendMessage(Component.text("   / ____/_____ ___   ___   _____ (_)____ _"));
        PROXY_SERVER.sendMessage(Component.text("  / /_   / ___// _ \\ / _ \\ / ___// // __ `/"));
        PROXY_SERVER.sendMessage(Component.text(" / __/  / /   /  __//  __/(__  )/ // /_/ / "));
        PROXY_SERVER.sendMessage(Component.text("/_/    /_/    \\___/ \\___//____//_/ \\__,_/  "));
        PROXY_SERVER.sendMessage(Component.text("\n     Powered by YesSteveModel and all contributors, Version: 2.3.2-universal"));
        PROXY_SERVER.sendMessage(Component.text("----------------------------------------------------------------"));
    }

    @Subscribe
    public void onProxyStart(ProxyInitializeEvent event) {
        INSTANCE = this;
        LOGGER = this.logger;
        PROXY_SERVER = this.proxyServer;

        EntryPoint.initLogger(this.logger); // Common module

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
        mapperManager = new YsmMapperPayloadManager(DefaultYsmPacketProxyImpl::new, VirtualYsmPacketProxyImpl::new);
        // Register mc packet listener
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        // Attach to ysm channel
        this.proxyServer.getChannelRegistrar().register(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY);
        // Init tracker
        tracker.init();
        tracker.addRealPlayerTrackerEventListener(mapperManager::onRealPlayerTrackerUpdate);
        tracker.addVirtualPlayerTrackerEventListener(mapperManager::onVirtualPlayerTrackerUpdate);

        // Init virtual player manager
        virtualPlayerManager.init();

        // Master controller service
        masterServer = new NettySocketServer(FreesiaConfig.masterServiceAddress, c -> new MasterServerMessageHandler());
        masterServer.bind();

        LOGGER.info("Initiating client kicker.");

        // Client detection
        kickChecker = new YsmClientKickingDetector();
        kickChecker.bootstrap();

        LOGGER.info("Registering commands");
        DispatchWorkerCommandCommand.register();
        ListYsmPlayersCommand.register();
    }

    @Subscribe
    public EventTask onPlayerDisconnect(@NotNull DisconnectEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            mapperManager.onPlayerDisconnect(targetPlayer);
            kickChecker.onPlayerLeft(targetPlayer);
        });
    }

    @Subscribe
    public EventTask onPlayerConnected(@NotNull ServerConnectedEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            // On first connect
            if (!mapperManager.hasPlayer(targetPlayer)) {
                this.logger.info("Initiating mapper session for player {}", targetPlayer.getUsername());

                // Create mapper session
                mapperManager.firstCreateMapper(targetPlayer);

                // Add to client kicker
                kickChecker.onPlayerJoin(targetPlayer);
                return;
            }

            // Player might switch its current server
            logger.info("Player {} has changed backend server. Reconnecting mapper session", targetPlayer.getUsername());
            // So, reconnect mapper session
            mapperManager.reconnectWorker(targetPlayer);
        });
    }

    @Subscribe
    public void onServerPreConnect(@NotNull ServerPreConnectEvent event) {
        mapperManager.updateRealPlayerEntityId(event.getPlayer(), -1); // Reset player's entity id to -1 as non initialized to prevent incorrect tracker status update
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event) {
        final ChannelIdentifier identifier = event.getIdentifier();
        final byte[] data = event.getData();

        if ((identifier instanceof MinecraftChannelIdentifier mineId) && (event.getSource() instanceof Player player)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());

            // TODO Need a packet rate limiter here?
            mapperManager.onPluginMessageIn(player, mineId, data);
        }
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        // Hook join packet for fetching player's entity id for the tracker
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            final WrapperPlayServerJoinGame playerSpawnPacket = new WrapperPlayServerJoinGame(event);
            final Player target = event.getPlayer();

            logger.info("Entity id update for player {} to {}", target.getUsername(), playerSpawnPacket.getEntityId());
            // Update id and try notifying update once
            mapperManager.updateRealPlayerEntityId(target, playerSpawnPacket.getEntityId());
        }
    }

    // We need to push off the packet process of worker because the player's login packet might still not reached to the client when we create the mapper session
    @Subscribe
    public void onServerPostConnect(@NotNull ServerPostConnectEvent postConnectEvent) {
        final Player target = postConnectEvent.getPlayer();

        // Retire callbacks of worker ysm packet processing
        mapperManager.onBackendReady(target);
    }
}
