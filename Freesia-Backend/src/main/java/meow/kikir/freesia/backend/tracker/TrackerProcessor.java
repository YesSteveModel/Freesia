package meow.kikir.freesia.backend.tracker;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.netty.buffer.Unpooled;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import meow.kikir.freesia.backend.FreesiaBackend;
import meow.kikir.freesia.backend.Utils;
import meow.kikir.freesia.backend.event.CyanidinRealPlayerTrackerUpdateEvent;
import meow.kikir.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerProcessor implements Listener {
    public static final String CHANNEL_NAME = "freesia:tracker_sync";

    // The default tracker event which is provided by Paper
    @EventHandler
    public void onPlayerTrackEntity(@NotNull PlayerTrackEntityEvent trackEvent) {
        final Player tracker = trackEvent.getPlayer();
        final Entity tracked = trackEvent.getEntity();

        if (tracked instanceof Player beingWatchedPlayer) {
            this.playerTrackedPlayer(beingWatchedPlayer, tracker);
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

    @EventHandler
    public void onPlayerUntrackEntity(@NotNull PlayerUntrackEntityEvent untrackEvent) {
        final Player watcher = untrackEvent.getPlayer();
        final Entity untracked = untrackEvent.getEntity();

        if (untracked instanceof Player trackedPlayer) {
            this.firePlayerUntracked(trackedPlayer.getUniqueId(), watcher.getUniqueId());
        }
    }

    private void playerTrackedPlayer(@NotNull Player beSeen, @NotNull Player seeing) {
        // Fire tracker update events
        if (!new CyanidinRealPlayerTrackerUpdateEvent(seeing, beSeen).callEvent()) {
            return;
        }

        // The true tracker update caller
        this.firePlayerTracked(seeing.getUniqueId(), beSeen.getUniqueId());
    }

    public void firePlayerTracked(UUID tracker, UUID tracked) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(0);
        wrappedUpdatePacket.writeUUID(tracker);
        wrappedUpdatePacket.writeUUID(tracked);

        // Find a payload
        final Player payload = Utils.randomPlayerIfNotFound(tracker);

        if (payload == null) {
            return;
        }

        final byte[] encoded = new byte[wrappedUpdatePacket.readableBytes()];
        wrappedUpdatePacket.readBytes(encoded);

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, encoded);
    }

    public void firePlayerUntracked(UUID tracker, UUID tracked) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(1);
        wrappedUpdatePacket.writeUUID(tracker);
        wrappedUpdatePacket.writeUUID(tracked);

        // Find a payload
        final Player payload = Utils.randomPlayerIfNotFound(tracker);

        if (payload == null) {
            return;
        }

        final byte[] encoded = new byte[wrappedUpdatePacket.readableBytes()];
        wrappedUpdatePacket.readBytes(encoded);

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, encoded);
    }
}
