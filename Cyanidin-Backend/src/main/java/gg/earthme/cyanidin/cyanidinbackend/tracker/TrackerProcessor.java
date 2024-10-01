package gg.earthme.cyanidin.cyanidinbackend.tracker;

import gg.earthme.cyanidin.cyanidinbackend.CyanidinBackend;
import gg.earthme.cyanidin.cyanidinbackend.Utils;
import gg.earthme.cyanidin.cyanidinbackend.event.CyanidinRealPlayerTrackerUpdateEvent;
import gg.earthme.cyanidin.cyanidinbackend.event.CyanidinTrackerScanEvent;
import gg.earthme.cyanidin.cyanidinbackend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrackerProcessor implements PluginMessageListener, Listener {
    private static final String CHANNEL_NAME = "cyanidin:tracker_sync";
    private final Map<Player, Set<Player>> visibleMap = new ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerLeft(@NotNull PlayerQuitEvent event){
        this.visibleMap.remove(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDead(@NotNull PlayerDeathEvent event){
        this.visibleMap.remove(event.getEntity());
        for (Set<Player> others : this.visibleMap.values()){
            others.remove(event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event){
        final Player player = event.getPlayer();

        this.visibleMap.put(player, new HashSet<>());
        this.visibleMap.get(player).add(player);

        Set<Player> visiblePlayers = new HashSet<>();

        visiblePlayers.add(player);

        for (Player singlePlayer : Bukkit.getOnlinePlayers()) {
            if (player.canSee(singlePlayer)) {
                visiblePlayers.add(singlePlayer);
                this.playerTrackedPlayer(player, singlePlayer);
            } else {
                if (this.visibleMap.containsKey(player) && this.visibleMap.get(player).contains(singlePlayer)) {
                    this.visibleMap.get(player).remove(singlePlayer);
                }
            }
        }

        this.visibleMap.replace(player, visiblePlayers);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event){
        this.visibleMap.put(event.getPlayer(), new HashSet<>());
        this.visibleMap.get(event.getPlayer()).add(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event){
        final Player player = event.getPlayer();
        Set<Player> visiblePlayers = new HashSet<>();

        visiblePlayers.add(player);

        for (Player singlePlayer : Bukkit.getOnlinePlayers()) {
            if (player.canSee(singlePlayer)) {
                visiblePlayers.add(singlePlayer);
                this.playerTrackedPlayer(player, singlePlayer);
            } else {
                if (this.visibleMap.containsKey(player) && this.visibleMap.get(player).contains(singlePlayer)) {
                    this.visibleMap.get(player).remove(singlePlayer);
                }
            }
        }

        this.visibleMap.replace(player, visiblePlayers);
    }

    private void playerTrackedPlayer(@NotNull Player watcher, @NotNull Player beSeeing){
        if (!new CyanidinRealPlayerTrackerUpdateEvent(watcher, beSeeing).callEvent()) {
            return;
        }

        this.notifyTrackerUpdate(watcher.getUniqueId(), beSeeing.getUniqueId());
    }

    public boolean notifyTrackerUpdate(UUID watcher, UUID beWatched) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(2);
        wrappedUpdatePacket.writeUUID(beWatched);
        wrappedUpdatePacket.writeUUID(watcher);

        final Player payload = Utils.randomPlayerIfNotFound(watcher);

        if (payload == null) {
            return false;
        }

        payload.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, wrappedUpdatePacket.getBytes());
        return true;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] data) {
        if (!channel.equals(CHANNEL_NAME)){
            return;
        }

        final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        if (packetData.readVarInt() == 1) {
            final int callbackId = packetData.readVarInt();
            final UUID requestedPlayerUUID = packetData.readUUID();

            final Player toScan = Objects.requireNonNull(Bukkit.getPlayer(requestedPlayerUUID));

            final Set<UUID> result = new HashSet<>();
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other == toScan) {
                    continue;
                }

                if (other.canSee(toScan)) {
                    result.add(other.getUniqueId());
                }
            }

            final CyanidinTrackerScanEvent trackerScanEvent = new CyanidinTrackerScanEvent(result, toScan);

            Bukkit.getPluginManager().callEvent(trackerScanEvent);

            final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());

            reply.writeVarInt(0);
            reply.writeVarInt(callbackId);
            reply.writeVarInt(result.size());

            for (UUID uuid : result) {
                reply.writeUUID(uuid);
            }

            sender.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, reply.getBytes());
        }
    }
}
