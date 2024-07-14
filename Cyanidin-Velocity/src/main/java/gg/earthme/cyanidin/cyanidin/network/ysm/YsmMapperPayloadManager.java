package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.CyanidinConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key("yes_steve_model:1_2_1");
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier.create("yes_steve_model", "1_2_1");

    private final Map<Player, TcpClientSession> player2Mappers = new ConcurrentHashMap<>();
    private final Map<Player, MapperSessionProcessor> mapperSessions = new ConcurrentHashMap<>();
    private final Map<Player, Queue<Consumer<MapperSessionProcessor>>> mapperCreateCallbacks = new ConcurrentHashMap<>();

    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = new LinkedHashMap<>();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    private final Map<Player, Integer> player2WorkerEntityIds = new ConcurrentHashMap<>();
    private final Map<Player, Integer> player2ServerEntityIds = new ConcurrentHashMap<>();
    private final Set<Player> ysmInstalledPlayers = ConcurrentHashMap.newKeySet();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator) {
        this.packetProxyCreator = packetProxyCreator;
        this.backend2Players.put(CyanidinConfig.workerMSessionAddress ,1); //TODO Load balance
    }

    public void onClientYsmPacketReply(Player target){
        this.ysmInstalledPlayers.add(target);
    }

    public void onPacketProxyReady(Player player){
        this.mapperSessions.get(player).onProxyReady();
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId){
        if (!this.player2WorkerEntityIds.containsKey(target)){
            this.player2WorkerEntityIds.put(target, entityId);
            return;
        }

        this.player2WorkerEntityIds.replace(target, entityId);
    }

    public int getWorkerPlayerEntityId(Player target){
        if (!this.player2WorkerEntityIds.containsKey(target)){
            return -1;
        }

        return this.player2WorkerEntityIds.get(target);
    }

    public void updateServerPlayerEntityId(Player target, int entityId){
        if (!this.player2ServerEntityIds.containsKey(target)){
            this.player2ServerEntityIds.put(target, entityId);
            return;
        }

        this.player2ServerEntityIds.replace(target, entityId);
    }

    public int getServerPlayerEntityId(Player target){
        if (!this.player2ServerEntityIds.containsKey(target)){
            return -1;
        }

        return this.player2ServerEntityIds.get(target);
    }

    public void reconnectWorker(@NotNull Player master, @NotNull InetSocketAddress target){
        if (!this.mapperSessions.containsKey(master)){
            throw new IllegalStateException("Player is not connected to mapper!");
        }

        final MapperSessionProcessor currentMapper = this.mapperSessions.get(master);

        currentMapper.setKickMasterWhenDisconnect(false);
        currentMapper.getSession().disconnect("RECONNECT");
        currentMapper.waitForDisconnected();

        this.createMapperSession(master, target);
    }

    public void reconnectWorker(@NotNull Player master){
        this.reconnectWorker(master, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean hasPlayer(@NotNull Player player){
        return this.player2Mappers.containsKey(player);
    }

    public void onPlayerConnected(Player player){
        if (this.mapperCreateCallbacks.containsKey(player)){
            return;
        }

        this.mapperCreateCallbacks.put(player, new ConcurrentLinkedQueue<>());
    }

    public void firstCreateMapper(Player player){
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(Player target){
        return this.ysmInstalledPlayers.contains(target);
    }

    public void onPlayerDisconnect(Player player){
        this.ysmInstalledPlayers.remove(player);
        this.player2ServerEntityIds.remove(player);
        this.player2WorkerEntityIds.remove(player);

        final MapperSessionProcessor mapperSession = this.mapperSessions.remove(player);
        final Queue<Consumer<MapperSessionProcessor>> removedQueue = this.mapperCreateCallbacks.remove(player);

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
            mapperSession.getSession().disconnect("PLAYER DISCONNECTED");
        }

        this.player2Mappers.remove(player);
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster, Component reason){
        if (kickMaster) mapperSession.getBindPlayer().disconnect(Component.text("Backend disconnected, Reason:").append(reason)); //TODO I18N
        this.player2Mappers.remove(mapperSession.getBindPlayer());
        this.mapperSessions.remove(mapperSession.getBindPlayer());
        this.ysmInstalledPlayers.remove(mapperSession.getBindPlayer());

        final Queue<Consumer<MapperSessionProcessor>> removedQueue = this.mapperCreateCallbacks.get(mapperSession.getBindPlayer());

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

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel, byte[] packetData){
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)){
            return;
        }

        if (!this.player2Mappers.containsKey(player)){
            player.disconnect(Component.text("You are not connected to the backend.Please contact the administrators!")); //TODO I18N
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null || !mapperSession.isReadyForReceivingPackets()){
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
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
                                player.getUsername()),
                        null
                )
        );

        final MapperSessionProcessor packetProcessor = new MapperSessionProcessor(player, this.packetProxyCreator.apply(player), this);

        mapperSession.addListener(packetProcessor);

        mapperSession.setWriteTimeout(30000);
        mapperSession.setReadTimeout(30000);
        mapperSession.setConnectTimeout(3000);
        mapperSession.connect(true,false);

        while (!packetProcessor.isReadyForReceivingPackets()){
            LockSupport.parkNanos(1_000_000);
        }

        this.mapperSessions.put(player, packetProcessor);
        Consumer<MapperSessionProcessor> callback;
        while ((callback = this.mapperCreateCallbacks.get(player).poll()) != null){
            try {
                callback.accept(packetProcessor);
            }catch (Exception e){
                Cyanidin.LOGGER.info("Error occurs while processing connect callbacks!", e);
            }
        }
        packetProcessor.getPacketProxy().blockUntilProxyReady();

        this.player2Mappers.put(player, mapperSession);
    }

    public void onPlayerTrackerUpdate(Player owner, Player watching){
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(owner);

        if (mapperSession == null){
            this.mapperCreateCallbacks.get(owner).offer((mapper) -> ((DefaultYsmPacketProxyImpl) mapper.getPacketProxy()).sendEntityStateTo(watching));
            return;
        }

        ((DefaultYsmPacketProxyImpl) mapperSession.getPacketProxy()).sendEntityStateTo(watching);
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
