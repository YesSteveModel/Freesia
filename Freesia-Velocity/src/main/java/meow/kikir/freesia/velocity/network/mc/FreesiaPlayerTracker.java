package meow.kikir.freesia.velocity.network.mc;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class FreesiaPlayerTracker {
    private static final MinecraftChannelIdentifier SYNC_CHANNEL_KEY = MinecraftChannelIdentifier.create("freesia", "tracker_sync");

    private final Set<BiConsumer<Player, UUID>> realPlayerListeners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> trackerVisibleMap = new ConcurrentHashMap<>();

    public void init() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(SYNC_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getEventManager().register(Freesia.INSTANCE, this);
    }

    @Subscribe
    public void onPlayerDisconnect(@NonNull DisconnectEvent disconnectEvent) {
        final Player disconnected = disconnectEvent.getPlayer();

        // force clean up
        this.trackerVisibleMap.remove(disconnected.getUniqueId());
    }

    @Subscribe
    public void onPlayerPreConnect(@NonNull ServerPreConnectEvent preConnectEvent) {
        final Player connected = preConnectEvent.getPlayer();

        // force clean up
        this.trackerVisibleMap.put(connected.getUniqueId(), ConcurrentHashMap.newKeySet());
    }

    @Subscribe
    public void onChannelMsg(@NotNull PluginMessageEvent event) {
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        if (!event.getIdentifier().getId().equals(SYNC_CHANNEL_KEY.getId())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        final SimpleFriendlyByteBuf packetData = new SimpleFriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

        final int packetId = packetData.readVarInt();
        switch (packetId) {
            // add pairing
            case 0 -> {
                final UUID tracker = packetData.readUUID();
                final UUID tracked = packetData.readUUID();

                final Optional<Player> trackerPlayerOptional = Freesia.PROXY_SERVER.getPlayer(tracker);

                if (trackerPlayerOptional.isEmpty()) {
                    Freesia.LOGGER.warn("Unknown add pairing request for player {} !", tracker);
                    return;
                }

                final Player trackerPlayer = trackerPlayerOptional.get();

                final Set<UUID> visibleSubTable = this.trackerVisibleMap.get(tracker);
                // cleaned up, return
                if (visibleSubTable == null) {
                    return;
                }

                if (visibleSubTable.add(tracked)) {
                    this.firePlayerTracked(trackerPlayer, tracked);
                }
            }

            // remove pairing
            case 1 -> {
                final UUID tracker = packetData.readUUID();
                final UUID unTracked = packetData.readUUID();

                final Set<UUID> visibleSubTable = this.trackerVisibleMap.get(tracker);
                // cleaned up, return
                if (visibleSubTable == null) {
                    return;
                }

                visibleSubTable.remove(unTracked); // TODO - Untrack callbacks?
            }

            default -> throw new IllegalStateException("Unknown tracker sync packet id " + packetId + " !");
        }
    }

    public Set<Player> getVisiblePlayersTo(UUID beingWatched) {
        final Set<UUID> visibleTable = this.trackerVisibleMap.get(beingWatched);

        if (visibleTable == null) {
            return Collections.emptySet();
        }

        return visibleTable.stream()
                .map(uuid -> Freesia.PROXY_SERVER.getPlayer(uuid))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    public void firePlayerTracked(Player watcher, UUID watched) {
        for (BiConsumer<Player, UUID> listener : this.realPlayerListeners) {
            try {
                listener.accept(watcher, watched);
            } catch (Exception e) {
                Freesia.LOGGER.error("Can not process real tracker update!", e);
            }
        }
    }

    public void addTrackerListener(BiConsumer<Player, UUID> listener) {
        this.realPlayerListeners.add(listener);
    }
}