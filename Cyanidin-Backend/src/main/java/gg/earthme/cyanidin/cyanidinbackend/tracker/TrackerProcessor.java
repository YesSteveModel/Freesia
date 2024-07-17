package gg.earthme.cyanidin.cyanidinbackend.tracker;

import gg.earthme.cyanidin.cyanidinbackend.CyanidinBackend;
import gg.earthme.cyanidin.cyanidinbackend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(2);
        wrappedUpdatePacket.writeUUID(beSeeing.getUniqueId());
        wrappedUpdatePacket.writeUUID(watcher.getUniqueId());

        watcher.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, wrappedUpdatePacket.array());
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

            final Set<Player> result = new HashSet<>();
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other == toScan) {
                    continue;
                }

                if (other.canSee(toScan)) {
                    result.add(other);
                }
            }

            final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());

            reply.writeVarInt(0);
            reply.writeVarInt(callbackId);
            reply.writeVarInt(result.size());

            for (Player player : result) {
                reply.writeUUID(player.getUniqueId());
            }

            sender.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, reply.array());
        }
    }
}
