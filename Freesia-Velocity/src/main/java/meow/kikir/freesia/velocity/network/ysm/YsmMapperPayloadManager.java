package meow.kikir.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConfig;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    // Ysm channel key name
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key(YsmProtocolMetaFile.getYsmChannelNamespace() + ":" + YsmProtocolMetaFile.getYsmChannelPath());
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier.create(YsmProtocolMetaFile.getYsmChannelNamespace(), YsmProtocolMetaFile.getYsmChannelPath());

    // Used for virtual players like NPCs
    private final Map<UUID, YsmPacketProxy> virtualProxies = Maps.newHashMap();
    private final Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual;

    // Player to worker mappers connections
    private final Map<Player, MapperSessionProcessor> mapperSessions = Maps.newConcurrentMap();
    // Real player proxy factory
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    // Backend connect infos
    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = Maps.newLinkedHashMap();

    // The players who installed ysm(Used for packet sending reduction)
    private final Set<UUID> ysmInstalledPlayers = Sets.newConcurrentHashSet();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator, Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual) {
        this.packetProxyCreator = packetProxyCreator;
        this.packetProxyCreatorVirtual = packetProxyCreatorVirtual;
        this.backend2Players.put(FreesiaConfig.workerMSessionAddress, 1); //TODO Load balance
    }

    public void onClientYsmHandshakePacketReply(@NotNull Player target) {
        this.ysmInstalledPlayers.add(target.getUniqueId());
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId){
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerWorkerEntityId(entityId);
    }

    public void updateRealPlayerEntityId(Player target, int entityId){
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerEntityId(entityId);
    }

    public CompletableFuture<Boolean> setVirtualPlayerEntityState(UUID playerUUID, NBTCompound nbt) {
        final YsmPacketProxy virtualProxy;

        synchronized (this.virtualProxies) {
            virtualProxy = this.virtualProxies.get(playerUUID);
        }

        if (virtualProxy == null) {
            return CompletableFuture.completedFuture(false);
        }

        virtualProxy.setEntityDataRaw(nbt);
        virtualProxy.notifyFullTrackerUpdates();

        final NBTCompound entityData = virtualProxy.getCurrentEntityState();

        //Probably be reset
        if (entityData == null) {
            return CompletableFuture.completedFuture(true);
        }

        // Async io
        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
            try {
                final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(bos);

                serializer.serializeTag(dos, entityData, true);
                dos.flush();

                Freesia.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray()).whenComplete((unused, ex) -> {
                    if (ex != null) {
                        callback.completeExceptionally(ex);
                        return;
                    }

                    callback.complete(true);
                });
            } catch (Exception e) {
                callback.completeExceptionally(e);
            }
        }).schedule();

        return callback;
    }

    public CompletableFuture<Boolean> addVirtualPlayer(UUID playerUUID, int playerEntityId) {
        if (Freesia.PROXY_SERVER.getPlayer(playerUUID).isPresent()) {
            return CompletableFuture.completedFuture(false);
        }

        synchronized (this.virtualProxies) {
            if (this.virtualProxies.containsKey(playerUUID)) {
                return CompletableFuture.completedFuture(false);
            }

            final CompletableFuture<Boolean> callback = new CompletableFuture<>();

            final YsmPacketProxy createdVirtualProxy = this.virtualProxies.computeIfAbsent(playerUUID, this.packetProxyCreatorVirtual);

            //Load from data storage
            Freesia.virtualPlayerDataStorageManager.loadPlayerData(playerUUID).whenComplete((data, ex) -> {
                if (ex != null) {
                    callback.completeExceptionally(ex);
                }

                if (data == null) {
                    createdVirtualProxy.setPlayerEntityId(playerEntityId);
                    createdVirtualProxy.setPlayerWorkerEntityId(0);
                    callback.complete(true);
                    return;
                }

                try {
                    final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                    final NBTCompound read = (NBTCompound) serializer.deserializeTag(NBTLimiter.forBuffer(data, Integer.MAX_VALUE), new DataInputStream(new ByteArrayInputStream(data)), true);

                    createdVirtualProxy.setEntityDataRaw(read);
                    createdVirtualProxy.setPlayerEntityId(playerEntityId);
                    createdVirtualProxy.setPlayerWorkerEntityId(0);
                } catch (Exception ex1) {
                    callback.completeExceptionally(ex1);
                    return;
                }

                callback.complete(true);
            });

            return callback;
        }
    }

    public CompletableFuture<Boolean> removeVirtualPlayer(UUID playerUUID) {
        final YsmPacketProxy removedProxy;

        synchronized (this.virtualProxies) {
            removedProxy = this.virtualProxies.remove(playerUUID);

            if (removedProxy == null) {
                return CompletableFuture.completedFuture(false);
            }
        }

        final NBTCompound entityData = removedProxy.getCurrentEntityState();

        if (entityData == null) {
            return CompletableFuture.completedFuture(true);
        }

        final CompletableFuture<Boolean> callback = new CompletableFuture<>();

        Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
            try {
                final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(bos);

                serializer.serializeTag(dos, entityData, true);
                dos.flush();

                Freesia.virtualPlayerDataStorageManager.save(playerUUID, bos.toByteArray()).whenComplete((r, e) -> {
                    if (e != null) {
                        callback.completeExceptionally(e);
                        return;
                    }

                    callback.complete(true);
                });
            } catch (Exception e) {
                callback.completeExceptionally(e);
            }
        }).schedule();

        return callback;
    }

    private void disconnectMapperWithoutKickingMaster(@NotNull MapperSessionProcessor connection) {
        connection.setKickMasterWhenDisconnect(false);
        connection.destroyAndAwaitDisconnected();
    }

    public Map<Integer, RealPlayerYsmPacketProxyImpl> collectRealProxy2WorkerEntityId() {
        final Map<Integer, RealPlayerYsmPacketProxyImpl> result = Maps.newLinkedHashMap();

        // Here we act likes a COWList
        final Collection<MapperSessionProcessor> copied = new ArrayList<>(this.mapperSessions.values());

        for (MapperSessionProcessor session : copied) {
            final YsmPacketProxy packetProxy = session.getPacketProxy();

            // If it's real player
            if (packetProxy instanceof RealPlayerYsmPacketProxyImpl realPlayerProxy) {
                final int playerEntityId = realPlayerProxy.getPlayerEntityId();
                final int workerEntityId = realPlayerProxy.getPlayerWorkerEntityId();

                if (playerEntityId != -1 && workerEntityId != -1) { // check if it's ready
                    result.put(workerEntityId, realPlayerProxy);
                }
            }
        }

        return result;
    }

    public void autoCreateMapper(Player player) {
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(@NotNull Player target) {
        return this.ysmInstalledPlayers.contains(target.getUniqueId());
    }

    public boolean isPlayerInstalledYsm(UUID target) {
        return this.ysmInstalledPlayers.contains(target);
    }

    public void onPlayerDisconnect(@NotNull Player player) {
        this.ysmInstalledPlayers.remove(player.getUniqueId());

        final MapperSessionProcessor mapperSession = this.mapperSessions.remove(player);

        if (mapperSession != null) {
            this.disconnectMapperWithoutKickingMaster(mapperSession);
        }
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster, @Nullable Component reason) {
        // Kick the master it binds
        if (kickMaster)
            mapperSession.getBindPlayer().disconnect(Freesia.languageManager.i18n(
                    FreesiaConstants.LanguageConstants.WORKER_TERMINATED_CONNECTION,
                    List.of("reason"),
                    List.of(reason == null ? Component.text("DISCONNECTED MANUAL") : reason)
            ));

        // Remove from list
        this.mapperSessions.remove(mapperSession.getBindPlayer());
    }

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel, byte[] packetData) {
        // Check if it is the message of ysm
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)) {
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            // Actually it shouldn't be and never be happened
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void onBackendReady(Player player) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            //race condition: already disconnected
            return;
        }

        mapperSession.onBackendReady();
    }

    public boolean disconnectAlreadyConnected(Player player) {
        final MapperSessionProcessor current = this.mapperSessions.get(player);

        // Not exists or created
        if (current == null) {
            return false;
        }

        // Will do remove in the callback
        this.disconnectMapperWithoutKickingMaster(current);
        return true;
    }

    public void initMapperPacketProcessor(@NotNull Player player) {
        final MapperSessionProcessor possiblyExisting = this.mapperSessions.get(player);

        if (possiblyExisting != null) {
            throw new IllegalStateException("Mapper session already exists for player " + player.getUsername());
        }

        final YsmPacketProxy packetProxy = this.packetProxyCreator.apply(player);
        final MapperSessionProcessor processor = new MapperSessionProcessor(player, packetProxy, this);

        packetProxy.setParentHandler(processor);

        this.mapperSessions.put(player, processor);
    }

    public void createMapperSession(@NotNull Player player, @NotNull InetSocketAddress backend) {
        // Instance new session
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

        // Our packet processor for packet forwarding
        final MapperSessionProcessor packetProcessor = this.mapperSessions.get(player);

        if (packetProcessor == null) {
            // Should be created in ServerPreConnectEvent
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        packetProcessor.setSession(mapperSession);
        mapperSession.addListener(packetProcessor);

        // Default as Minecraft client
        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT,30_000);
        mapperSession.setFlag(BuiltinFlags.WRITE_TIMEOUT,30_000);

        // Do connect
        mapperSession.connect(true,false);
    }

    public void onVirtualPlayerTrackerUpdate(UUID owner, Player watcher) {
        final YsmPacketProxy virtualProxy = this.virtualProxies.get(owner);

        //There is no specified virtual proxy for the owner
        if (virtualProxy == null) {
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) {
            virtualProxy.sendEntityStateTo(watcher);
        }
    }

    public void onRealPlayerTrackerUpdate(Player beingWatched, Player watcher) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(beingWatched);

        // The mapper was created earlier than the player's connection turned in-game state
        // so as the result, we could simply pass it down directly
        if (mapperSession == null) {
            // Should not be happened
            // We use random player as the payload of custom payload of freesia tracker, so there is a possibility
            // that race condition would happen between the disconnect logic and tracker update logic
            //throw new IllegalStateException("???");
            return;
        }

        if (this.isPlayerInstalledYsm(watcher)) { // Skip players who don't install ysm
            // Check if ready
            if (!mapperSession.queueTrackerUpdate(watcher.getUniqueId())) {
                mapperSession.getPacketProxy().sendEntityStateTo(watcher);
            }
        }
    }

    @Nullable
    private InetSocketAddress selectLessPlayer() {
        this.backendIpsAccessLock.readLock().lock();
        try {
            InetSocketAddress result = null;

            int idx = 0;
            int lastCount = 0;
            for (Map.Entry<InetSocketAddress, Integer> entry : this.backend2Players.entrySet()) {
                final InetSocketAddress currAddress = entry.getKey();
                final int currPlayerCount = entry.getValue();

                if (idx == 0) {
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                if (currPlayerCount < lastCount) {
                    lastCount = currPlayerCount;
                    result = currAddress;
                }

                idx++;
            }

            return result;
        } finally {
            this.backendIpsAccessLock.readLock().unlock();
        }
    }
}
