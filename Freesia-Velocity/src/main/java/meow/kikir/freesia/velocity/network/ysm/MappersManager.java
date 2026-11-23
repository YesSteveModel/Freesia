package meow.kikir.freesia.velocity.network.ysm;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.channel.unix.DomainSocketAddress;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConfig;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.velocity.network.ysm.hack.UnixDomainsSocketClientSession;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.network.tcp.TcpSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class MappersManager {
    // Ysm channel key name
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key(YsmProtocolMetaFile.getYsmChannelNamespace() + ":" + YsmProtocolMetaFile.getYsmChannelPath());
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier.create(YsmProtocolMetaFile.getYsmChannelNamespace(), YsmProtocolMetaFile.getYsmChannelPath());

    // Player to worker mappers connections
    private final Map<Player, MapperConnectionHandler> sessions = Maps.newConcurrentMap();
    private final Map<Integer, MapperConnectionHandler> backendId2Mapper = Maps.newConcurrentMap();
    private final Map<Integer, MapperConnectionHandler> workerId2Mapper = Maps.newConcurrentMap();
    // Real player proxy factory
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    // Backend connect infos
    private final ReadWriteLock workerTcpSessionIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<SocketAddress, Integer> workerIp2PlayerCounters = Maps.newLinkedHashMap();

    // The players who installed ysm(Used for packet sending reduction)
    private final Set<UUID> ysmInstalledPlayers = Sets.newConcurrentHashSet();
    // TODO - Remove - This is a hotfix for ysm
    private final Set<UUID> handshakeRepliedInitiallyPlayers = Sets.newConcurrentHashSet();

    public MappersManager(Function<Player, YsmPacketProxy> packetProxyCreator) {
        this.packetProxyCreator = packetProxyCreator;

        for (SocketAddress singleWorkerMsessionAddress : FreesiaConfig.workerMessionAddresses) {
            this.workerIp2PlayerCounters.put(singleWorkerMsessionAddress, 0);
        }
    }

    public void decreaseWorkerSessionCount(SocketAddress worker) {
        this.workerTcpSessionIpsAccessLock.writeLock().lock();
        try {
            final Integer old = this.workerIp2PlayerCounters.get(worker);
            if (old == null) {
                Freesia.LOGGER.warn("Trying to decrease session count for unregisted worker msession address: {}!", worker);
                return;
            }

            this.workerIp2PlayerCounters.put(worker, Math.max(0, old - 1));
        }finally {
            this.workerTcpSessionIpsAccessLock.writeLock().unlock();
        }
    }

    public void increaseWorkerSessionCount(SocketAddress worker) {
        this.workerTcpSessionIpsAccessLock.writeLock().lock();
        try {
            final Integer old = this.workerIp2PlayerCounters.get(worker);
            if (old == null) {
                Freesia.LOGGER.warn("Trying to increase session count for unregisted worker msession address: {}!", worker);
                return;
            }

            this.workerIp2PlayerCounters.put(worker, old + 1);
        }finally {
            this.workerTcpSessionIpsAccessLock.writeLock().unlock();
        }
    }

    public void onClientYsmHandshakePacketReply(@NotNull Player target) {
        final UUID targetUUID = target.getUniqueId();

        this.ysmInstalledPlayers.add(targetUUID);
        // TODO - Remove after fixed
        this.handshakeRepliedInitiallyPlayers.add(targetUUID);
    }

    // TODO - Remove after fixed
    public boolean isInitiallyHandshakeReplied(@NonNull Player player) {
        return this.handshakeRepliedInitiallyPlayers.contains(player.getUniqueId());
    }

    public void updateWorkerPlayerEntityId(Player target, int entityId){
        final MapperConnectionHandler mapper = this.sessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerWorkerEntityId(entityId);

        this.workerId2Mapper.put(entityId, mapper);
    }

    public void updateRealPlayerEntityId(Player target, int entityId){
        final MapperConnectionHandler mapper = this.sessions.get(target);

        if (mapper == null) {
            throw new IllegalStateException("Mapper not created yet!");
        }

        mapper.getPacketProxy().setPlayerEntityId(entityId);

        this.backendId2Mapper.put(entityId, mapper);
    }

    private void disconnectMapperWithoutKickingMaster(@NotNull MapperConnectionHandler connection) {
        connection.setKickMasterWhenDisconnect(false);
        connection.destroyAndAwaitDisconnected();
    }

    public MapperConnectionHandler sessionProcessorByEntityId(int entityId) {
        return this.backendId2Mapper.get(entityId);
    }

    public MapperConnectionHandler sessionProcessorByWorkerEntityId(int workerEntityId) {
        return this.workerId2Mapper.get(workerEntityId);
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
        final UUID playerUUID = player.getUniqueId();

        this.ysmInstalledPlayers.remove(playerUUID);
        // TODO - Remove after fixed
        this.handshakeRepliedInitiallyPlayers.remove(playerUUID);

        final MapperConnectionHandler mapperSession = this.sessions.remove(player);

        if (mapperSession != null) {
            this.disconnectMapperWithoutKickingMaster(mapperSession);
        }
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperConnectionHandler mapperSession, boolean kickMaster, @Nullable Component reason) {
        // Kick the master it binds
        if (kickMaster)
            mapperSession.getBindPlayer().disconnect(Freesia.languageManager.i18n(
                    FreesiaConstants.LanguageConstants.WORKER_TERMINATED_CONNECTION,
                    List.of("reason"),
                    List.of(reason == null ? Component.text("DISCONNECTED MANUAL") : reason)
            ));

        // Remove from list
        this.sessions.remove(mapperSession.getBindPlayer());

        // remove entity id mappings (backend)
        final int backendEntityId = mapperSession.getPacketProxy().getPlayerEntityId();
        if (backendEntityId != -1) {
            this.backendId2Mapper.remove(backendEntityId);
        }

        // remove entity id mappings (worker)
        final int workerEntityId = mapperSession.getPacketProxy().getPlayerWorkerEntityId();
        if (workerEntityId != -1) {
            this.workerId2Mapper.remove(workerEntityId);
        }

        final SocketAddress workerAddress = mapperSession.getWorkerAddress();
        if (workerAddress != null) {
            this.decreaseWorkerSessionCount(workerAddress);
        }
    }

    public void onPluginMessageIn(@NotNull Player player, @NotNull MinecraftChannelIdentifier channel, byte[] packetData) {
        // Check if it is the message of ysm
        if (!channel.equals(YSM_CHANNEL_KEY_VELOCITY)) {
            return;
        }

        final MapperConnectionHandler mapperSession = this.sessions.get(player);

        if (mapperSession == null) {
            // Actually it shouldn't be and never be happened
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        mapperSession.processPlayerPluginMessage(packetData);
    }

    public void onBackendReady(Player player) {
        final MapperConnectionHandler mapperSession = this.sessions.get(player);

        if (mapperSession == null) {
            //race condition: already disconnected
            return;
        }

        mapperSession.onBackendReady();
    }

    public boolean disconnectAlreadyConnected(Player player) {
        final MapperConnectionHandler current = this.sessions.get(player);

        // Not exists or created
        if (current == null) {
            return false;
        }

        // Will do remove in the callback
        this.disconnectMapperWithoutKickingMaster(current);
        return true;
    }

    public void initMapperPacketProcessor(@NotNull Player player) {
        final MapperConnectionHandler possiblyExisting = this.sessions.get(player);

        if (possiblyExisting != null) {
            throw new IllegalStateException("Mapper session already exists for player " + player.getUsername());
        }

        final YsmPacketProxy packetProxy = this.packetProxyCreator.apply(player);
        final MapperConnectionHandler processor = new MapperConnectionHandler(player, packetProxy, this);

        packetProxy.setParentHandler(processor);

        this.sessions.put(player, processor);
    }

    public void createMapperSession(@NotNull Player player, @NotNull SocketAddress backend) {
        final MinecraftProtocol mcProtocol = new MinecraftProtocol(
                new GameProfile(
                        player.getUniqueId(),
                        player.getUsername()),
                null
        );

        // Instance new session
        final TcpSession mapperSession = backend instanceof DomainSocketAddress dsa ? new UnixDomainsSocketClientSession(
                dsa.path(),
                mcProtocol
        ) :
        new TcpClientSession(
                ((InetSocketAddress) backend).getHostName(),
                ((InetSocketAddress) backend).getPort(),
                mcProtocol
        );

        // Our packet processor for packet forwarding
        final MapperConnectionHandler packetProcessor = this.sessions.get(player);

        if (packetProcessor == null) {
            // Should be created in ServerPreConnectEvent
            throw new IllegalStateException("Mapper session not found or ready for player " + player.getUsername());
        }

        packetProcessor.setSession(mapperSession);
        mapperSession.addListener(packetProcessor);

        // Default as Minecraft client
        mapperSession.setFlag(BuiltinFlags.READ_TIMEOUT,30_000);
        mapperSession.setFlag(BuiltinFlags.WRITE_TIMEOUT,30_000);

        packetProcessor.setWorkerAddress(backend);

        // Do connect
        mapperSession.connect(true,false);

        this.increaseWorkerSessionCount(backend);
    }

    public void handlePlayerTracked(Player watcher, UUID watched) {
        final Player beingWatchedRealPlayer = Freesia.PROXY_SERVER.getPlayer(watched).orElse(null);

        YsmPacketProxy packetProxy = null;
        boolean doActualUpdate = this.isPlayerInstalledYsm(watcher);

        // not a real player, go into virtual player process
        if (beingWatchedRealPlayer == null) {
            // TODO
            return;
        }else {
            final MapperConnectionHandler mapperSession = this.sessions.get(beingWatchedRealPlayer);

            // handle race cond: player removed before tracker update
            if (mapperSession != null) {
                packetProxy = mapperSession.getPacketProxy();

                // player is ysm installed but mapper is not ready yet
                if (doActualUpdate && mapperSession.queueTrackerUpdate(watcher.getUniqueId())) {
                    doActualUpdate = false;
                }
            }
        }

        if (packetProxy != null && doActualUpdate) {
            packetProxy.sendFullEntityDataTo(watcher);
        }
    }

    @Nullable
    private SocketAddress selectLessPlayer() {
        this.workerTcpSessionIpsAccessLock.readLock().lock();
        try {
            SocketAddress result = null;

            int idx = 0;
            int lastCount = 0;
            for (Map.Entry<SocketAddress, Integer> entry : this.workerIp2PlayerCounters.entrySet()) {
                final SocketAddress currAddress = entry.getKey();
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
            this.workerTcpSessionIpsAccessLock.readLock().unlock();
        }
    }
}
