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
import java.util.concurrent.ConcurrentHashMap;
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

    // Backend connect infos
    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = Maps.newLinkedHashMap();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    // The players who installed ysm(Used for packet sending reduction)
    private final Set<Player> ysmInstalledPlayers = Sets.newConcurrentHashSet();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator, Function<UUID, YsmPacketProxy> packetProxyCreatorVirtual) {
        this.packetProxyCreator = packetProxyCreator;
        this.packetProxyCreatorVirtual = packetProxyCreatorVirtual;
        this.backend2Players.put(FreesiaConfig.workerMSessionAddress, 1); //TODO Load balance
    }

    public void onClientYsmHandshakePacketReply(Player target) {
        if (this.ysmInstalledPlayers.contains(target)) {
            return;
        }

        this.ysmInstalledPlayers.add(target);
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId){
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            return;
        }

        mapper.getPacketProxy().setPlayerWorkerEntityId(entityId);
    }

    public void updateRealPlayerEntityId(Player target, int entityId){
        final MapperSessionProcessor mapper = this.mapperSessions.get(target);

        if (mapper == null) {
            return;
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
        virtualProxy.refreshToOthers();

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
                    callback.complete(true);
                    return;
                }

                try {
                    final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                    final NBTCompound read = (NBTCompound) serializer.deserializeTag(NBTLimiter.forBuffer(data, Integer.MAX_VALUE), new DataInputStream(new ByteArrayInputStream(data)), true);

                    createdVirtualProxy.setEntityDataRaw(read);
                    createdVirtualProxy.setPlayerEntityId(playerEntityId); // Will fired tracker update when entity id changed
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

    public void reconnectWorker(@NotNull Player master, @NotNull InetSocketAddress target) {
        final MapperSessionProcessor currentMapper = this.mapperSessions.get(master);

        if (currentMapper == null) {
            throw new IllegalStateException("Player is not connected to mapper!");
        }

        currentMapper.setKickMasterWhenDisconnect(false);
        currentMapper.getSession().disconnect("RECONNECT");
        currentMapper.waitForDisconnected();

        this.createMapperSession(master, target);
    }

    public void reconnectWorker(@NotNull Player master) {
        this.reconnectWorker(master, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean hasPlayer(@NotNull Player player) {
        return this.mapperSessions.containsKey(player);
    }

    public void firstCreateMapper(Player player) {
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public boolean isPlayerInstalledYsm(Player target) {
        return this.ysmInstalledPlayers.contains(target);
    }

    public void onPlayerDisconnect(Player player) {
        this.ysmInstalledPlayers.remove(player);

        final MapperSessionProcessor mapperSession = this.mapperSessions.remove(player);

        if (mapperSession != null) {
            mapperSession.setKickMasterWhenDisconnect(false); // Player already offline, so we don't disconnect again
            mapperSession.getSession().disconnect("PLAYER DISCONNECTED");
            mapperSession.waitForDisconnected();
        }
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster, Component reason) {
        if (kickMaster)
            mapperSession.getBindPlayer().disconnect(Freesia.languageManager.i18n(FreesiaConstants.LanguageConstants.WORKER_TERMINATED_CONNECTION, List.of("reason"), List.of(reason)));
        this.mapperSessions.remove(mapperSession.getBindPlayer());
    }

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel, byte[] packetData) {
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)) {
            return;
        }

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void onBackendReady(Player player) {
        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession == null) {
            // Shouldn't be happened
            throw new IllegalStateException("???");
        }

        mapperSession.onBackendReady();
    }

    public void createMapperSession(@NotNull Player player, @NotNull InetSocketAddress backend) {
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

        this.mapperSessions.put(player, packetProcessor); // Add to the mappers

        // Do connect
        mapperSession.connect(true,false);
    }

    public void onProxyLoggedin(Player player, MapperSessionProcessor packetProcessor, TcpClientSession session){
        // TODO : Are we still using this callback ?
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
            throw new IllegalStateException("???");
        }

        if (this.isPlayerInstalledYsm(watcher)) {
            mapperSession.getPacketProxy().sendEntityStateTo(watcher);
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
