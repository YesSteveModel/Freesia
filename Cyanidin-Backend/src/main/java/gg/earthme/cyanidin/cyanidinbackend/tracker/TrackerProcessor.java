package gg.earthme.cyanidin.cyanidinbackend.tracker;

import gg.earthme.cyanidin.cyanidinbackend.CyanidinBackend;
import gg.earthme.cyanidin.cyanidinbackend.Utils;
import gg.earthme.cyanidin.cyanidinbackend.event.CyanidinRealPlayerTrackerUpdateEvent;
import gg.earthme.cyanidin.cyanidinbackend.event.CyanidinTrackerScanEvent;
import gg.earthme.cyanidin.cyanidinbackend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerProcessor implements PluginMessageListener, Listener {
    private static final String CHANNEL_NAME = "cyanidin:tracker_sync";

    @EventHandler
    public void onPlayerTrack(PlayerTrackEntityEvent trackEntityEvent) {
        final Player watcher = trackEntityEvent.getPlayer();
        final Entity beWatching = trackEntityEvent.getEntity();

        if (beWatching instanceof Player beWatchingPlayer) {
            this.playerTrackedPlayer(watcher, beWatchingPlayer);
        }
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

            sender.getScheduler().execute(
                    CyanidinBackend.INSTANCE,
                    () -> {
                        Bukkit.getPluginManager().callEvent(trackerScanEvent);

                        final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());

                        reply.writeVarInt(0);
                        reply.writeVarInt(callbackId);
                        reply.writeVarInt(result.size());

                        for (UUID uuid : result) {
                            reply.writeUUID(uuid);
                        }

                        sender.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, reply.getBytes());
                    },
                    null,
                    1
            );
        }
    }
}
