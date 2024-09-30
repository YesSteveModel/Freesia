package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.CyanidinConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key("yes_steve_model:2_2_2");
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier.create("yes_steve_model", "2_2_2");

    // Used for virtual players like NPCs
    private final Map<UUID, YsmPacketProxy> virtualProxies = new HashMap<>();
    private final Map<UUID, Integer> virtualPlayerEntityIds = new HashMap<>();
    private final Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual;

    // Player to worker mappers connections
    private final Map<Player, TcpClientSession> player2Mappers = new ConcurrentHashMap<>();
    private final Map<Player, MapperSessionProcessor> mapperSessions = new ConcurrentHashMap<>();
    // Creation callbacks
    private final Map<Player, Queue<Consumer<MapperSessionProcessor>>> mapperCreateCallbacks = new ConcurrentHashMap<>();

    // Backend connect infos
    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = new LinkedHashMap<>();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    // The entity id of the worker session(Used for tracker updates remapping)
    private final Map<Player, Integer> player2WorkerEntityIds = new ConcurrentHashMap<>();
    // The entity id of the real server session(Used for tracker updates remapping)
    private final Map<Player, Integer> player2ServerEntityIds = new ConcurrentHashMap<>();
    // The players who installed ysm(Used for packet sending reduction)
    private final Set<Player> ysmInstalledPlayers = ConcurrentHashMap.newKeySet();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator, Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual) {
        this.packetProxyCreator = packetProxyCreator;
        this.packetProxyCreatorVirtual = packetProxyCreatorVirtual;
        this.backend2Players.put(CyanidinConfig.workerMSessionAddress ,1); //TODO Load balance
    }

    public void onClientYsmHandshakePacketReply(Player target){
        if (this.ysmInstalledPlayers.contains(target)) {
            return;
        }

        this.ysmInstalledPlayers.add(target);
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

    public void updateRealPlayerEntityId(Player target, int entityId){
        if (!this.player2ServerEntityIds.containsKey(target)){
            this.player2ServerEntityIds.put(target, entityId);
            return;
        }

        this.player2ServerEntityIds.replace(target, entityId);
    }

    public int getRealPlayerEntityId(Player target){
        if (!this.player2ServerEntityIds.containsKey(target)){
            return -1;
        }

        return this.player2ServerEntityIds.get(target);
    }

    public void setVirtualPlayerEntityState(UUID playerUUID, NBTCompound nbt){
        final YsmPacketProxy virtualProxy;

        synchronized (this.virtualProxies){
            virtualProxy = this.virtualProxies.get(playerUUID);
        }

        if (virtualProxy == null){
            return;
        }

        virtualProxy.setEntityDataRaw(nbt);
        virtualProxy.refreshToOthers();

        final NBTCompound entityData = virtualProxy.getCurrentEntityState();

        if (entityData == null){
            return;
        }

        try {
            final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            serializer.serializeTag(dos, entityData, true);
            dos.flush();

            Cyanidin.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public boolean addVirtualPlayer(UUID playerUUID, int playerEntityId){
        if (Cyanidin.PROXY_SERVER.getPlayer(playerUUID).isPresent()){
            return false;
        }

        synchronized (this.virtualProxies){
            if (this.virtualProxies.containsKey(playerUUID)){
                return false;
            }

            final YsmPacketProxy createdVirtualProxy = this.virtualProxies.computeIfAbsent(playerUUID, this.packetProxyCreatorVirtual);

            this.updateVirtualPlayerEntityId(playerUUID, playerEntityId);

            //Load from data storage
            Cyanidin.virtualPlayerDataStorageManager.loadPlayerData(playerUUID).whenComplete((data, ex) -> {
                if (ex != null){
                    throw new RuntimeException(ex);
                }

                try {
                    final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                    final NBTCompound read = (NBTCompound) serializer.deserializeTag(new NBTLimiter(data), new DataInputStream(new ByteArrayInputStream(data)), true);

                    createdVirtualProxy.setEntityDataRaw(read);
                    createdVirtualProxy.refreshToOthers();
                }catch (Exception ex1){
                    throw new RuntimeException(ex1);
                }
            });
        }

        return true;
    }

    public boolean removeVirtualPlayer(UUID playerUUID){
        final CompletableFuture<Void> saveWaiter = new CompletableFuture<>() ;

        final YsmPacketProxy removedProxy;
        synchronized (this.virtualProxies){
            removedProxy = this.virtualProxies.remove(playerUUID);

            if (removedProxy == null){
                return false;
            }

            this.virtualPlayerEntityIds.remove(playerUUID);
        }

        final NBTCompound entityData = removedProxy.getCurrentEntityState();

        if (entityData == null){
            return true;
        }

        try {
            final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            serializer.serializeTag(dos, entityData, true);
            dos.flush();

            Cyanidin.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray()).whenComplete((r, e) -> {
                if (e != null){
                    saveWaiter.completeExceptionally(e);
                    return;
                }

                saveWaiter.complete(null);
            });
        }catch (Exception e){
            throw new RuntimeException(e);
        }

        saveWaiter.join();
        return true;
    }

    public int getVirtualPlayerEntityId(UUID target){
        if (!this.virtualPlayerEntityIds.containsKey(target)){
            return -1;
        }

        return this.virtualPlayerEntityIds.get(target);
    }

    public void updateVirtualPlayerEntityId(UUID target, int entityId){
        if (!this.virtualPlayerEntityIds.containsKey(target)){
            this.virtualPlayerEntityIds.put(target, entityId);
            return;
        }

        this.virtualPlayerEntityIds.replace(target, entityId);
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
        this.mapperCreateCallbacks.putIfAbsent(player, new ConcurrentLinkedQueue<>());
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
            mapperSession.setKickMasterWhenDisconnect(false); //Player already offline, so we don't disconnect again
            mapperSession.getSession().disconnect("PLAYER DISCONNECTED");
            mapperSession.waitForDisconnected();
        }

        this.player2Mappers.remove(player);
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster, Component reason){
        if (kickMaster) mapperSession.getBindPlayer().disconnect(Cyanidin.languageManager.i18n("cyanidin.backend.disconnected", List.of("reason"), List.of(reason)));
        this.player2Mappers.remove(mapperSession.getBindPlayer());
        this.mapperSessions.remove(mapperSession.getBindPlayer());

        final Queue<Consumer<MapperSessionProcessor>> removedQueue = this.mapperCreateCallbacks.get(mapperSession.getBindPlayer());

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

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel, byte[] packetData){
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)){
            return;
        }

        if (!this.player2Mappers.containsKey(player)){
            player.disconnect(Cyanidin.languageManager.i18n("cyanidin.backend.not_connected", Collections.emptyList(), Collections.emptyList()));
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null){
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

        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT,30_000);
        mapperSession.setFlag(BuiltinFlags.WRITE_TIMEOUT,30_000);
        mapperSession.connect(true,false);
    }

    public void onProxyLoggedin(Player player, MapperSessionProcessor packetProcessor, TcpClientSession session){
        this.mapperSessions.put(player, packetProcessor);
        this.player2Mappers.put(player, session);

        //Finish the callbacks
        Cyanidin.PROXY_SERVER.getScheduler().buildTask(Cyanidin.INSTANCE, () -> {
            packetProcessor.getPacketProxy().blockUntilProxyReady();

            Consumer<MapperSessionProcessor> callback;
            while ((callback = this.mapperCreateCallbacks.get(player).poll()) != null){
                try {
                    callback.accept(packetProcessor);
                }catch (Exception e){
                    Cyanidin.LOGGER.info("Error occurs while processing connect callbacks!", e);
                }
            }
        }).schedule();
    }

    public void onVirtualPlayerTrackerUpdate(UUID owner, Player watcher){
        final YsmPacketProxy virtualProxy = this.virtualProxies.get(owner);

        //There is no specified virtual proxy for the owner
        if (virtualProxy == null){
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)){
            virtualProxy.sendEntityStateTo(watcher);
        }
    }

    public void onRealPlayerTrackerUpdate(Player owner, Player watcher){
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(owner);

        if (mapperSession == null){
            //Commit to callback if the mapper session of the player not finished connecting currently
            this.mapperCreateCallbacks.computeIfAbsent(owner, player -> new ConcurrentLinkedQueue<>()).offer((mapper) -> {
                if (mapper == null){
                    return;
                }

                mapper.getPacketProxy().sendEntityStateTo(watcher);
            });
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)){
            mapperSession.getPacketProxy().sendEntityStateTo(watcher);
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
