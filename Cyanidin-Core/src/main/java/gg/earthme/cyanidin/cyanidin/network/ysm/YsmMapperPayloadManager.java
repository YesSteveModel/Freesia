package gg.earthme.cyanidin.cyanidin.network.ysm;

import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.CyanidinConfig;
import gg.earthme.cyanidin.cyanidin.SchedulerUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key("yes_steve_model:2_2_2");
    public static final NamespacedKey YSM_CHANNEL_KEY_VELOCITY = NamespacedKey.fromString("yes_steve_model:2_2_2");

    // Player to worker mappers connections
    private final Map<UUID, TcpClientSession> player2Mappers = new ConcurrentHashMap<>();
    private final Map<UUID, MapperSessionProcessor> mapperSessions = new ConcurrentHashMap<>();
    // Creation callbacks
    private final Map<UUID, Queue<Consumer<MapperSessionProcessor>>> mapperCreateCallbacks = new ConcurrentHashMap<>();

    // Backend connect infos
    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = new LinkedHashMap<>();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    // The entity id of the worker session(Used for tracker updates remapping)
    private final Map<UUID, Integer> player2WorkerEntityIds = new ConcurrentHashMap<>();
    // The entity id of the real server session(Used for tracker updates remapping)
    private final Map<UUID, Integer> player2ServerEntityIds = new ConcurrentHashMap<>();
    // The players who installed ysm(Used for packet sending reduction)
    private final Set<UUID> ysmInstalledPlayers = ConcurrentHashMap.newKeySet();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator) {
        this.packetProxyCreator = packetProxyCreator;
        this.backend2Players.put(CyanidinConfig.workerMSessionAddress ,1); //TODO Load balance
    }

    public void disconnectAllMappers(){
        for (TcpClientSession mapperSession : this.player2Mappers.values()){
            try {
                mapperSession.disconnect(Cyanidin.languageManager.i18n("cyanidin.backend.disconnected_actively", List.of(), List.of()));
            }catch (Exception e){
                Cyanidin.LOGGER.error("Failed to disconnect mapper session", e);
            }
        }

        this.player2Mappers.clear();
    }

    public void onClientYsmPacketReply(Player target){
        this.ysmInstalledPlayers.add(target.getUniqueId());
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId){
        final UUID targetUUID = target.getUniqueId();

        if (!this.player2WorkerEntityIds.containsKey(targetUUID)){
            this.player2WorkerEntityIds.put(targetUUID, entityId);
            return;
        }

        this.player2WorkerEntityIds.replace(targetUUID, entityId);
    }

    public int getWorkerPlayerEntityId(Player target){
        final UUID targetUUID = target.getUniqueId();

        if (!this.player2WorkerEntityIds.containsKey(targetUUID)){
            return -1;
        }

        return this.player2WorkerEntityIds.get(targetUUID);
    }

    public void updateServerPlayerEntityId(Player target, int entityId){
        final UUID targetUUID = target.getUniqueId();

        if (!this.player2ServerEntityIds.containsKey(targetUUID)){
            this.player2ServerEntityIds.put(targetUUID, entityId);
            return;
        }

        this.player2ServerEntityIds.replace(targetUUID, entityId);
    }

    public int getServerPlayerEntityId(Player target){
        final UUID targetUUID = target.getUniqueId();

        if (!this.player2ServerEntityIds.containsKey(targetUUID)){
            return -1;
        }

        return this.player2ServerEntityIds.get(targetUUID);
    }

    public void reconnectWorker(@NotNull Player master, @NotNull InetSocketAddress target){
        final UUID masterUUID = master.getUniqueId();

        if (!this.mapperSessions.containsKey(masterUUID)){
            throw new IllegalStateException("Player is not connected to mapper!");
        }

        final MapperSessionProcessor currentMapper = this.mapperSessions.get(masterUUID);

        currentMapper.setKickMasterWhenDisconnect(false);
        currentMapper.getSession().disconnect("RECONNECT");
        currentMapper.waitForDisconnected();

        this.createMapperSession(master, target);
    }

    public void reconnectWorker(@NotNull Player master){
        this.reconnectWorker(master, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean hasPlayer(@NotNull Player player){
        return this.player2Mappers.containsKey(player.getUniqueId());
    }

    public void onPlayerConnected(Player player){
        this.mapperCreateCallbacks.putIfAbsent(player.getUniqueId(), new ConcurrentLinkedQueue<>());
    }

    public void firstCreateMapper(Player player){
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(Player target){
        return this.ysmInstalledPlayers.contains(target.getUniqueId());
    }

    public void onPlayerDisconnect(Player player){
        final UUID playerUUID = player.getUniqueId();
        this.ysmInstalledPlayers.remove(playerUUID);
        this.player2ServerEntityIds.remove(playerUUID);
        this.player2WorkerEntityIds.remove(playerUUID);

        final MapperSessionProcessor mapperSession = this.mapperSessions.remove(playerUUID);
        final Queue<Consumer<MapperSessionProcessor>> removedQueue = this.mapperCreateCallbacks.remove(playerUUID);

        if (removedQueue != null){
            Consumer<MapperSessionProcessor> unprocessed;
            while ((unprocessed = removedQueue.poll()) != null){
                try {
                    unprocessed.accept(mapperSession);
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Failed to retire connect callback!", e);
                }
            }
        }

        if (mapperSession != null){
            mapperSession.setKickMasterWhenDisconnect(false); //Player already offline, so we don't disconnect again
            mapperSession.getSession().disconnect("PLAYER DISCONNECTED");
            mapperSession.waitForDisconnected();
        }

        this.player2Mappers.remove(playerUUID);
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster, Component reason){
        if (kickMaster) mapperSession.getBindPlayer().kick(Cyanidin.languageManager.i18n("cyanidin.backend.disconnected", List.of("reason"), List.of(reason)));
        this.ysmInstalledPlayers.remove(mapperSession.getBindPlayer().getUniqueId());
        this.player2Mappers.remove(mapperSession.getBindPlayer().getUniqueId());
        this.mapperSessions.remove(mapperSession.getBindPlayer().getUniqueId());

        final Queue<Consumer<MapperSessionProcessor>> removedQueue = this.mapperCreateCallbacks.get(mapperSession.getBindPlayer().getUniqueId());

        //Finalize the callbacks
        if (removedQueue != null){
            Consumer<MapperSessionProcessor> unprocessed;
            while ((unprocessed = removedQueue.poll()) != null){
                try {
                    unprocessed.accept(mapperSession);
                }catch (Exception e){
                    Cyanidin.LOGGER.error("Failed to retire connect callback!", e);
                }
            }
        }
    }

    public void onPluginMessageIn(@NotNull Player player, @NotNull NamespacedKey channel, byte[] packetData){
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)){
            return;
        }

        if (!this.player2Mappers.containsKey(player.getUniqueId())){
            player.kick(Cyanidin.languageManager.i18n("cyanidin.backend.not_connected", Collections.emptyList(), Collections.emptyList()));
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player.getUniqueId());

        if (mapperSession == null){
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getName());
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void createMapperSession(@NotNull Player player, @NotNull InetSocketAddress backend){
        final TcpClientSession mapperSession = new TcpClientSession(
                backend.getHostName(),
                backend.getPort(),
                new MinecraftProtocol(
                        new GameProfile(
                                player.getUniqueId(),
                                player.getName()),
                        null
                )
        );

        final MapperSessionProcessor packetProcessor = new MapperSessionProcessor(player, this.packetProxyCreator.apply(player), this);

        mapperSession.addListener(packetProcessor);

        mapperSession.setFlag(BuiltinFlags.CLIENT_CONNECT_TIMEOUT, 30_000);
        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT, 30_000);
        mapperSession.connect(true,false);
    }

    public void onProxyLoggedin(Player player, MapperSessionProcessor packetProcessor, TcpClientSession session){
        this.mapperSessions.put(player.getUniqueId(), packetProcessor);
        this.player2Mappers.put(player.getUniqueId(), session);

        //Finish the callbacks
        SchedulerUtils.getAsyncScheduler().execute(() -> {
            packetProcessor.getPacketProxy().blockUntilProxyReady();

            Consumer<MapperSessionProcessor> callback;
            while ((callback = this.mapperCreateCallbacks.get(player.getUniqueId()).poll()) != null){
                try {
                    callback.accept(packetProcessor);
                }catch (Exception e){
                    Cyanidin.LOGGER.info("Error occurs while processing connect callbacks!", e);
                }
            }
        });
    }

    public void onPlayerTrackerUpdate(Player owner, Player watching){
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(owner.getUniqueId());

        if (mapperSession == null){
            //Commit to callback if the mapper session of the player not finished connecting currently
            this.mapperCreateCallbacks.computeIfAbsent(owner.getUniqueId(), player -> new ConcurrentLinkedQueue<>()).offer((mapper) -> {
                if (mapper == null){
                    return;
                }

                ((DefaultYsmPacketProxyImpl) mapper.getPacketProxy()).sendEntityStateTo(watching);
            });
            return;
        }

        if (this.isPlayerInstalledYsm(watching)){
            ((DefaultYsmPacketProxyImpl) mapperSession.getPacketProxy()).sendEntityStateTo(watching);
        }
    }

    @Nullable
    private InetSocketAddress selectLessPlayer(){
        this.backendIpsAccessLock.readLock().lock();
        try {
            InetSocketAddress result = null;

            int idx = 0;
            int lastCount = 0;
            for (Map.Entry<InetSocketAddress, Integer> entry : this.backend2Players.entrySet()){
                final InetSocketAddress currAddress = entry.getKey();
                final int currPlayerCount = entry.getValue();

                if (idx == 0){
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                if (currPlayerCount < lastCount){
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                idx++;
            }

            return result;
        }finally {
            this.backendIpsAccessLock.readLock().unlock();
        }
    }
}
