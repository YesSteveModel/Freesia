package meow.kikir.freesia.backend.tracker;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.netty.buffer.Unpooled;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import meow.kikir.freesia.backend.FreesiaBackend;
import meow.kikir.freesia.backend.Utils;
import meow.kikir.freesia.backend.event.CyanidinRealPlayerTrackerUpdateEvent;
import meow.kikir.freesia.backend.event.CyanidinTrackerScanEvent;
import meow.kikir.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerProcessor implements PluginMessageListener, Listener {
    public static final String CHANNEL_NAME = "freesia:tracker_sync";

    // The default tracker event which is provided by Paper
    @EventHandler
    public void onPlayerTrackEntity(@NotNull PlayerTrackEntityEvent trackEvent) {
        final Player watcher = trackEvent.getPlayer();
        final Entity beingWatched = trackEvent.getEntity();

        if (beingWatched instanceof Player beingWatchedPlayer) {
            this.playerTrackedPlayer(beingWatchedPlayer, watcher);
        }
    }

    // We can use this event to track when a player is added to the world
    // That's because there is no player respawn event on folia but folia's respawn logic will fire it when performing a respawn
    @EventHandler
    public void onPlayerAddedToWorld(@NotNull EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Player player) {
            this.playerTrackedPlayer(player, player);
        }
    }

    private void playerTrackedPlayer(@NotNull Player beSeen, @NotNull Player seeing) {
        // Fire tracker update events
        if (!new CyanidinRealPlayerTrackerUpdateEvent(seeing, beSeen).callEvent()) {
            return;
        }

        // The true tracker update caller
        this.notifyTrackerUpdate(seeing.getUniqueId(), beSeen.getUniqueId());
    }

    public void notifyTrackerUpdate(UUID watcher, UUID beWatched) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(2);
        wrappedUpdatePacket.writeUUID(beWatched);
        wrappedUpdatePacket.writeUUID(watcher);

        // Find a payload
        final Player payload = Utils.randomPlayerIfNotFound(watcher);

        if (payload == null) {
            return;
        }

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, wrappedUpdatePacket.getBytes());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] data) {
        if (!channel.equals(CHANNEL_NAME)) {
            return;
        }

        final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        if (packetData.readVarInt() == 1) {
            final int callbackId = packetData.readVarInt();
            final UUID requestedPlayerUUID = packetData.readUUID();

            final Player toScan = Objects.requireNonNull(Bukkit.getPlayer(requestedPlayerUUID));

            final Set<UUID> result = new HashSet<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.canSee(toScan)) {
                    result.add(player.getUniqueId());
                }
            }

            final CyanidinTrackerScanEvent trackerScanEvent = new CyanidinTrackerScanEvent(result, toScan);

            // We need to schedule back to pass the dumb async catchers as it was firing from both netty threads and main threads
            sender.getScheduler().execute(
                    FreesiaBackend.INSTANCE,
                    () -> {
                        Bukkit.getPluginManager().callEvent(trackerScanEvent);

                        final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());

                        reply.writeVarInt(0);
                        reply.writeVarInt(callbackId);
                        reply.writeVarInt(result.size());

                        for (UUID uuid : result) {
                            reply.writeUUID(uuid);
                        }

                        sender.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, reply.getBytes());
                    },
                    null,
                    1
            );
        }
    }

}
