package meow.kikir.freesia.velocity;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import io.github._4drian3d.vpacketevents.api.event.PacketSendEvent;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.NettySocketServer;
import meow.kikir.freesia.velocity.command.ListYsmPlayersCommand;
import meow.kikir.freesia.velocity.command.DispatchWorkerCommandCommand;
import meow.kikir.freesia.velocity.command.ReloadModelsCommand;
import meow.kikir.freesia.velocity.i18n.I18NManager;
import meow.kikir.freesia.velocity.network.backend.MasterServerMessageHandler;
import meow.kikir.freesia.velocity.network.mc.FreesiaPlayerTracker;
import meow.kikir.freesia.velocity.network.ysm.RealPlayerYsmPacketProxy;
import meow.kikir.freesia.velocity.network.ysm.MappersManager;
import meow.kikir.freesia.velocity.storage.DefaultRealPlayerDataStorageManagerImpl;
import meow.kikir.freesia.velocity.storage.IDataStorageManager;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Plugin(id = "freesia", name = "Freesia", version = BuildConstants.VERSION, authors = {"EarthMe", "HappyRespawnanchor", "xiaozhangup"}, dependencies = @Dependency(id = "vpacketevents"))
public class Freesia {
    public static final FreesiaPlayerTracker tracker = new FreesiaPlayerTracker();

    public static final IDataStorageManager realPlayerDataStorageManager = new DefaultRealPlayerDataStorageManagerImpl();
    public static final Map<UUID, MasterServerMessageHandler> registedWorkers = Maps.newConcurrentMap();

    public static final I18NManager languageManager = new I18NManager();

    public static Freesia INSTANCE = null;
    public static Logger LOGGER = null;
    public static ProxyServer PROXY_SERVER = null;
    public static YsmClientHandShakeTimer kickChecker;
    public static MappersManager mappersManager;
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
        PROXY_SERVER.sendMessage(Component.text("\n     Powered by YesSteveModel and all contributors, Version: " + BuildConstants.VERSION));
        PROXY_SERVER.sendMessage(Component.text("----------------------------------------------------------------"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        masterServer.close();
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
        mappersManager = new MappersManager(RealPlayerYsmPacketProxy::new);
        // Attach to ysm channel
        this.proxyServer.getChannelRegistrar().register(MappersManager.YSM_CHANNEL_KEY_VELOCITY);
        // Init tracker
        tracker.init();
        tracker.addTrackerListener(mappersManager::handlePlayerTracked);

        // Master controller service
        masterServer = new NettySocketServer(FreesiaConfig.masterServiceAddress, MasterServerMessageHandler::new);
        masterServer.bind();

        LOGGER.info("Initiating client kicker.");

        // Client detection
        kickChecker = new YsmClientHandShakeTimer();
        kickChecker.bootstrap();

        LOGGER.info("Registering commands");
        DispatchWorkerCommandCommand.register();
        ListYsmPlayersCommand.register();
        ReloadModelsCommand.register();
    }

    @Subscribe
    public EventTask onPlayerDisconnect(@NotNull DisconnectEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            mappersManager.onPlayerDisconnect(targetPlayer);
            kickChecker.onPlayerLeft(targetPlayer);
        });
    }

    @Subscribe
    public EventTask onPlayerConnected(@NotNull ServerConnectedEvent event) {
        final Player targetPlayer = event.getPlayer();

        return EventTask.async(() -> {
            // Add to client kicker
            kickChecker.onPlayerJoin(targetPlayer);
        });
    }

    @Subscribe
    public EventTask onServerPreConnect(@NotNull ServerPreConnectEvent event) {
        final Player player = event.getPlayer();

        // Create mapper processor here
        return EventTask.async(() -> {
            final boolean potentialDisconnected = mappersManager.disconnectAlreadyConnected(player);

            if (potentialDisconnected) {
                // Player switched server, do log
                logger.info("Player {} has changed backend server. Reconnecting mapper session", player.getUsername());
            }

            // Re init after removed or init on first connected
            mappersManager.initMapperPacketProcessor(player);

            // Create or re-create mapper session

            this.logger.info("Initiating mapper session for player {}", player.getUsername());

            mappersManager.autoCreateMapper(player);
        });
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event) {
        final ChannelIdentifier identifier = event.getIdentifier();
        final byte[] data = event.getData();

        if ((identifier instanceof MinecraftChannelIdentifier mineId) && (event.getSource() instanceof Player player)) {
            // skip non-ysm channel
            if (!mineId.equals(MappersManager.YSM_CHANNEL_KEY_VELOCITY)) {
                return;
            }
            
            event.setResult(PluginMessageEvent.ForwardResult.handled());

            // TODO Need a packet rate limiter here?
            mappersManager.onPluginMessageIn(player, mineId, data);
        }
    }

    @Subscribe
    public void onPacketSend(@NotNull PacketSendEvent event) {
        final MinecraftPacket packet = event.getPacket();
        if (packet instanceof JoinGamePacket joinGamePacket) {
            final Player target = event.getPlayer();
            final int entityId = joinGamePacket.getEntityId();

            logger.info("Entity id update for player {} to {}", target.getUsername(), entityId);

            // Update id and try notifying update once
            mappersManager.updateRealPlayerEntityId(target, entityId);

            // Finalize callbacks
            PROXY_SERVER.getScheduler().buildTask(this, () -> mappersManager.onBackendReady(target)).schedule();
        }
    }

    /********************************API Part****************************************/
    public static Freesia getInstance() {
        return INSTANCE;
    }

    public static void reloadAllModels() {
        final List<MasterServerMessageHandler> currConnectedWorkers = new ArrayList<>(registedWorkers.values());

        for (MasterServerMessageHandler workerHandler : currConnectedWorkers) {
            workerHandler.callWorkerModelReload();
        }
    }

    public static MasterServerMessageHandler getWorkerConnectionByUUID(UUID workerUUID) {
        return registedWorkers.get(workerUUID);
    }

    public static void callWorkerCommand(String command, Consumer<Component> callbackForEachCall) {
        final List<MasterServerMessageHandler> currConnectedWorkers = new ArrayList<>(registedWorkers.values());

        for (MasterServerMessageHandler workerHandler : currConnectedWorkers) {
            workerHandler.dispatchCommandToWorker(command, callbackForEachCall);
        }
    }

    public static void callWorkerCommand(UUID workerUUID, String command, Consumer<Component> callbackForEachCall) {
        final MasterServerMessageHandler workerHandler = registedWorkers.get(workerUUID);

        if (workerHandler != null) {
            workerHandler.dispatchCommandToWorker(command, callbackForEachCall);
        }
    }
}
