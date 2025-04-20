package meow.kikir.freesia.velocity.network.mc;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FreesiaPlayerTracker {
    private static final MinecraftChannelIdentifier SYNC_CHANNEL_KEY = MinecraftChannelIdentifier.create("freesia", "tracker_sync");

    private final Set<BiConsumer<Player, Player>> realPlayerListeners = ConcurrentHashMap.newKeySet();
    private final Set<BiConsumer<UUID, Player>> virtualPlayerListeners = ConcurrentHashMap.newKeySet();

    private final Map<Integer, Consumer<Set<UUID>>> pendingCanSeeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(0);

    public void init() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(SYNC_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getEventManager().register(Freesia.INSTANCE, this);
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

        final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

        switch (packetData.readVarInt()) {
            case 0 -> {
                final int taskId = packetData.readVarInt();
                final int collectionSize = packetData.readVarInt();
                final Set<UUID> result = new HashSet<>(collectionSize);

                for (int i = 0; i < collectionSize; i++) {
                    result.add(packetData.readUUID());
                }

                final Consumer<Set<UUID>> targetTask = this.pendingCanSeeTasks.remove(taskId);

                try {
                    targetTask.accept(result);
                } catch (Exception e) {
                    Freesia.LOGGER.error("Can not process tracker callback task !", e);
                }
            }

            case 2 -> {
                final UUID beSeeingUUID = packetData.readUUID();
                final UUID watcherUUID = packetData.readUUID();

                final Optional<Player> watcherPlayerNullable = Freesia.PROXY_SERVER.getPlayer(watcherUUID);
                final Optional<Player> beSeeingPlayerNullable = Freesia.PROXY_SERVER.getPlayer(beSeeingUUID);

                if (watcherPlayerNullable.isPresent()) {
                    final Player watcherPlayer = watcherPlayerNullable.get();

                    if (beSeeingPlayerNullable.isPresent()) {
                        final Player beSeeingPlayer = beSeeingPlayerNullable.get();

                        for (BiConsumer<Player, Player> listener : this.realPlayerListeners) {
                            try {
                                listener.accept(beSeeingPlayer, watcherPlayer);
                            } catch (Exception e) {
                                Freesia.LOGGER.error("Can not process real tracker update!", e);
                            }
                        }

                        return;
                    }

                    for (BiConsumer<UUID, Player> listener : this.virtualPlayerListeners) {
                        try {
                            listener.accept(beSeeingUUID, watcherPlayer);
                        } catch (Exception e) {
                            Freesia.LOGGER.error("Can not process virtual tracker update!", e);
                        }
                    }
                }
            }
        }
    }

    public CompletableFuture<Set<UUID>> getCanSee(@NotNull UUID target) {
        CompletableFuture<Set<UUID>> callback = new CompletableFuture<>();
        final int callbackId = this.idGenerator.getAndIncrement();

        this.pendingCanSeeTasks.put(callbackId, callback::complete);

        final FriendlyByteBuf callbackRequest = new FriendlyByteBuf(Unpooled.buffer());

        callbackRequest.writeVarInt(1);
        callbackRequest.writeVarInt(callbackId);
        callbackRequest.writeUUID(target);

        final Optional<Player> targetPlayerNullable = Freesia.PROXY_SERVER.getPlayer(target);

        final boolean[] cancelCallbackAdd = {false};
        if (targetPlayerNullable.isPresent()) {
            final Player targetPlayer = targetPlayerNullable.get();

            targetPlayer.getCurrentServer().ifPresentOrElse(
                    server -> server.getServer().sendPluginMessage(SYNC_CHANNEL_KEY, callbackRequest.getBytes()),
                    () -> {
                        cancelCallbackAdd[0] = true;
                        callback.complete(null); // Maybe at the early stage
                    } // Throw exception when we didn't find that server
            );
        } else {
            cancelCallbackAdd[0] = true;
            callback.complete(null);
        }

        // If we didn't find the server, we need to remove the callback
        if (cancelCallbackAdd[0]) {
            this.pendingCanSeeTasks.remove(callbackId);
        }

        return callback;
    }

    public void addVirtualPlayerTrackerEventListener(BiConsumer<UUID, Player> listener) {
        this.virtualPlayerListeners.add(listener);
    }

    public void addRealPlayerTrackerEventListener(BiConsumer<Player, Player> listener) {
        this.realPlayerListeners.add(listener);
    }
}