package gg.earthme.cyanidin.cyanidin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.google.common.collect.Maps;
import gg.earthme.cyanidin.cyanidin.storage.DefaultDataStorageManagerImpl;
import gg.earthme.cyanidin.cyanidin.storage.IDataStorageManager;
import gg.earthme.cyanidin.cyanidin.i18n.I18NManager;
import gg.earthme.cyanidin.cyanidin.network.backend.MasterServerMessageHandler;
import gg.earthme.cyanidin.cyanidin.network.mc.CyanidinPlayerTracker;
import gg.earthme.cyanidin.cyanidin.network.ysm.DefaultYsmPacketProxyImpl;
import gg.earthme.cyanidin.cyanidin.network.ysm.YsmMapperPayloadManager;
import gg.earthme.cyanidin.common.EntryPoint;
import gg.earthme.cyanidin.common.communicating.NettySocketServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class Cyanidin extends JavaPlugin implements PacketListener, Listener {

    public static Cyanidin INSTANCE = null;
    public static Logger LOGGER = null;

    public static final YsmMapperPayloadManager mapperManager = new YsmMapperPayloadManager(DefaultYsmPacketProxyImpl::new);
    public static final CyanidinPlayerTracker tracker = new CyanidinPlayerTracker();
    public static final IDataStorageManager dataStorageManager = new DefaultDataStorageManagerImpl();
    public static final Map<UUID, MasterServerMessageHandler> registedWorkers = Maps.newConcurrentMap();
    public static final I18NManager languageManager = new I18NManager();
    public static NettySocketServer masterServer;

    private static void printLogo(){
        Bukkit.getServer().sendMessage(Component.text("----------------------------------------------------------------"));
        Bukkit.getServer().sendMessage(Component.text("      ______                  _     ___     "));
        Bukkit.getServer().sendMessage(Component.text("     / ____/_  ______ _____  (_)___/ (_)___ "));
        Bukkit.getServer().sendMessage(Component.text("    / /   / / / / __ `/ __ \\/ / __  / / __ \\"));
        Bukkit.getServer().sendMessage(Component.text("   / /___/ /_/ / /_/ / / / / / /_/ / / / / /"));
        Bukkit.getServer().sendMessage(Component.text("   \\____/\\__, /\\__,_/_/ /_/_/\\__,_/_/_/ /_/ "));
        Bukkit.getServer().sendMessage(Component.text("        /____/                              "));
        Bukkit.getServer().sendMessage(Component.text("     Powered by CyanidinMC, Version: " + BuildConstants.VERSION));
        Bukkit.getServer().sendMessage(Component.text("----------------------------------------------------------------"));
    }

    @Override
    public void onDisable() {
        mapperManager.disconnectAllMappers();
        tracker.deRegisterAll();
        masterServer.close();
        SchedulerUtils.shutdownAsyncScheduler();
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        LOGGER = INSTANCE.getSLF4JLogger();
        EntryPoint.initLogger(this.getSLF4JLogger());
        SchedulerUtils.initAsyncExecutor();

        printLogo();

        try {
            CyanidinConfig.init();
            languageManager.loadLanguageFile(CyanidinConfig.languageName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Registering events and packet listeners.");
        Bukkit.getPluginManager().registerEvents(tracker, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
        tracker.addTrackerEventListener(mapperManager::onPlayerTrackerUpdate);

        masterServer = new NettySocketServer(CyanidinConfig.masterServiceAddress, c -> new MasterServerMessageHandler());
        masterServer.bind();
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event){
        final Player targetPlayer = event.getPlayer();

        mapperManager.onPlayerDisconnect(targetPlayer);
    }

    @EventHandler
    public void onPlayerJoined(@NotNull PlayerJoinEvent event){
        final Player targetPlayer = event.getPlayer();

        mapperManager.updateServerPlayerEntityId(targetPlayer, targetPlayer.getEntityId());
        mapperManager.firstCreateMapper(targetPlayer);
        mapperManager.onPlayerConnected(targetPlayer);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE){
            final WrapperPlayClientPluginMessage packet = new WrapperPlayClientPluginMessage(event);
            final String channel = packet.getChannelName();

            if (channel.equals(YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY.toString())){
                final Player target = (Player) event.getPlayer();
                final byte[] data = packet.getData();

                mapperManager.onPluginMessageIn(target, YsmMapperPayloadManager.YSM_CHANNEL_KEY_VELOCITY, data);
            }
        }
    }
}
