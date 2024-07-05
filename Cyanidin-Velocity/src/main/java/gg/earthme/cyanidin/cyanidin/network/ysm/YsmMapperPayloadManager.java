package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class YsmMapperPayloadManager {
    public static final Key YSM_CHANNEL_KEY_ADVENTURE = Key.key("yes_steve_model:1_2_1");
    public static final MinecraftChannelIdentifier YSM_CHANNEL_KEY_VELOCITY = MinecraftChannelIdentifier.create("yes_steve_model", "1_2_1");

    private final Map<Player, TcpClientSession> player2Mappers = new ConcurrentHashMap<>();
    private final Map<Player, MapperSessionProcessor> mapperSessions = new ConcurrentHashMap<>();

    private final ReadWriteLock backendIpsAccessLock = new ReentrantReadWriteLock(false);
    private final Map<InetSocketAddress, Integer> backend2Players = new LinkedHashMap<>();
    private final Function<Player, YsmPacketProxy> packetProxyCreator;

    private final Map<Player, Integer> player2EntityIds = new ConcurrentHashMap<>();

    public YsmMapperPayloadManager(Function<Player, YsmPacketProxy> packetProxyCreator) {
        this.packetProxyCreator = packetProxyCreator;
        this.backend2Players.put(Cyanidin.BACKEND_ADDRESS_MC,1);
    }

    public void onPacketProxyReady(Player player){
        this.mapperSessions.get(player).onProxyReady();
    }

    public void updatePlayerEntityId(Player target, int entityId){
        if (!this.player2EntityIds.containsKey(target)){
            this.player2EntityIds.put(target, entityId);
            return;
        }

        this.player2EntityIds.replace(target, entityId);
    }

    public int getPlayerEntityId(Player target){
        if (!this.player2EntityIds.containsKey(target)){
            return -1;
        }

        return this.player2EntityIds.get(target);
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

    public void onPlayerBackendConnected(Player player){
        this.createMapperSession(player, Objects.requireNonNull(this.selectLessPlayer()));
    }

    public void onPlayerDisconnect(Player player){
        this.player2EntityIds.remove(player);

        final MapperSessionProcessor mapperSession = this.mapperSessions.get(player);

        if (mapperSession != null){
            mapperSession.getSession().disconnect("PLAYER DISCONNECTED");
        }

        this.player2Mappers.remove(player);
        this.mapperSessions.remove(player);
    }

    protected void onWorkerSessionDisconnect(@NotNull MapperSessionProcessor mapperSession, boolean kickMaster){
        if (kickMaster) mapperSession.getBindPlayer().disconnect(Component.text("Backend disconnected")); //TODO I18N
        this.player2Mappers.remove(mapperSession.getBindPlayer());
        this.mapperSessions.remove(mapperSession.getBindPlayer());
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
        packetProcessor.getPacketProxy().blockUntilProxyReady();

        this.player2Mappers.put(player, mapperSession);
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
