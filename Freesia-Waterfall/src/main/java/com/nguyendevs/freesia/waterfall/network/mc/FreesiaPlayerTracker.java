package com.nguyendevs.freesia.waterfall.network.mc;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FreesiaPlayerTracker implements Listener {
    private static final String SYNC_CHANNEL_KEY = "freesia:tracker_sync";

    private final Set<BiConsumer<ProxiedPlayer, ProxiedPlayer>> realPlayerListeners = ConcurrentHashMap.newKeySet();
    private final Set<BiConsumer<UUID, ProxiedPlayer>> virtualPlayerListeners = ConcurrentHashMap.newKeySet();

    private final Map<Integer, Consumer<Set<UUID>>> pendingCanSeeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(0);

    public void init() {
        Freesia.PROXY_SERVER.registerChannel(SYNC_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getPluginManager().registerListener(Freesia.INSTANCE, this);
    }

    @EventHandler
    public void onChannelMsg(@NotNull PluginMessageEvent event) {
        if (!(event.getSender() instanceof Server)) {
            return;
        }

        if (!event.getTag().equals(SYNC_CHANNEL_KEY)) {
            return;
        }

        event.setCancelled(true);

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
                    Freesia.LOGGER.log(java.util.logging.Level.SEVERE, "Can not process tracker callback task !", e);
                }
            }

            case 2 -> {
                final UUID beSeeingUUID = packetData.readUUID();
                final UUID watcherUUID = packetData.readUUID();

                final ProxiedPlayer watcherPlayerNullable = Freesia.PROXY_SERVER.getPlayer(watcherUUID);
                final ProxiedPlayer beSeeingPlayerNullable = Freesia.PROXY_SERVER.getPlayer(beSeeingUUID);

                if (watcherPlayerNullable != null) {
                    final ProxiedPlayer watcherPlayer = watcherPlayerNullable;

                    if (beSeeingPlayerNullable != null) {
                        final ProxiedPlayer beSeeingPlayer = beSeeingPlayerNullable;

                        for (BiConsumer<ProxiedPlayer, ProxiedPlayer> listener : this.realPlayerListeners) {
                            try {
                                listener.accept(beSeeingPlayer, watcherPlayer);
                            } catch (Exception e) {
                                Freesia.LOGGER.log(java.util.logging.Level.SEVERE,
                                        "Can not process real tracker update!", e);
                            }
                        }

                        return;
                    }

                    for (BiConsumer<UUID, ProxiedPlayer> listener : this.virtualPlayerListeners) {
                        try {
                            listener.accept(beSeeingUUID, watcherPlayer);
                        } catch (Exception e) {
                            Freesia.LOGGER.log(java.util.logging.Level.SEVERE,
                                    "Can not process virtual tracker update!", e);
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

        final ProxiedPlayer targetPlayerNullable = Freesia.PROXY_SERVER.getPlayer(target);

        final boolean[] cancelCallbackAdd = { false };
        if (targetPlayerNullable != null) {
            final ProxiedPlayer targetPlayer = targetPlayerNullable;

            if (targetPlayer.getServer() != null) {
                targetPlayer.getServer().sendData(SYNC_CHANNEL_KEY, callbackRequest.getBytes());
            } else {
                cancelCallbackAdd[0] = true;
                callback.complete(null); // Maybe at the early stage
            }
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

    public void addVirtualPlayerTrackerEventListener(BiConsumer<UUID, ProxiedPlayer> listener) {
        this.virtualPlayerListeners.add(listener);
    }

    public void addRealPlayerTrackerEventListener(BiConsumer<ProxiedPlayer, ProxiedPlayer> listener) {
        this.realPlayerListeners.add(listener);
    }
}

