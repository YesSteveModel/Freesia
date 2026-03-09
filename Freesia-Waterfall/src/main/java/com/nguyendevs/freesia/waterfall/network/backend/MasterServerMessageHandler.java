package com.nguyendevs.freesia.waterfall.network.backend;

import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import com.nguyendevs.freesia.common.EntryPoint;
import com.nguyendevs.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import com.nguyendevs.freesia.common.communicating.message.m2w.M2WDispatchCommandMessage;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.events.PlayerEntityDataLoadEvent;
import com.nguyendevs.freesia.waterfall.events.PlayerEntityDataStoreEvent;
import com.nguyendevs.freesia.waterfall.events.WorkerConnectedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

public class MasterServerMessageHandler extends NettyServerChannelHandlerLayer {
    private final Map<Integer, Consumer<String>> pendingCommandDispatches = Maps.newConcurrentMap();
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);

    private volatile UUID workerUUID;
    private volatile String workerName;

    private volatile boolean commandDispatcherRetired = false;
    private final StampedLock commandDispatchCallbackLock = new StampedLock();

    public void dispatchCommandToWorker(String command, Consumer<Component> onDispatched) {
        final long stamp = this.commandDispatchCallbackLock.readLock();
        try {
            // We were retired during connection
            if (this.commandDispatcherRetired) {
                onDispatched.accept(null);
                return;
            }

            final int traceId = this.traceIdGenerator.getAndIncrement();

            final Consumer<String> wrappedDecoder = json -> {
                try {
                    // We were retired during disconnection
                    if (json == null) {
                        onDispatched.accept(null);
                        return;
                    }

                    final Component decoded = LegacyComponentSerializer.builder().build().deserialize(json);
                    onDispatched.accept(decoded);
                } catch (Exception e) {
                    EntryPoint.LOGGER_INST.error("Failed to decode command result from worker", e);
                    onDispatched.accept(null);
                }
            };

            this.pendingCommandDispatches.put(traceId, wrappedDecoder);
            this.sendMessage(new M2WDispatchCommandMessage(traceId, command));
        } finally {
            this.commandDispatchCallbackLock.unlockRead(stamp);
        }
    }

    @Nullable
    public UUID getWorkerUUID() {
        return this.workerUUID;
    }

    @Nullable

    public String getWorkerName() {
        return this.workerName;
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) {
        this.retireAllCommandDispatchCallbacks();

        if (this.workerUUID == null) {
            return;
        }

        Freesia.registedWorkers.remove(this.workerUUID);
    }

    private void retireAllCommandDispatchCallbacks() {
        final long stamp = this.commandDispatchCallbackLock.writeLock();
        try {
            this.commandDispatcherRetired = true;
            for (Map.Entry<Integer, Consumer<String>> entry : this.pendingCommandDispatches.entrySet()) {
                entry.getValue().accept(null);
            }

            this.pendingCommandDispatches.clear();
        } finally {
            this.commandDispatchCallbackLock.unlockWrite(stamp);
        }
    }

    @Override
    public CompletableFuture<byte[]> readPlayerData(UUID playerUUID) {
        final CompletableFuture<byte[]> callback = new CompletableFuture<>();
        Freesia.realPlayerDataStorageManager
                .loadPlayerData(playerUUID)
                .whenComplete((data, error) -> {
                    if (error != null) {
                        callback.completeExceptionally(error);
                        return;
                    }
                    try {
                        PlayerEntityDataLoadEvent event = new PlayerEntityDataLoadEvent(playerUUID, data);
                        Freesia.PROXY_SERVER.getPluginManager().callEvent(event);
                        callback.complete(event.getSerializedNbtData());
                    } catch (Exception e) {
                        callback.completeExceptionally(e);
                    }
                });
        return callback;
    }

    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content) {
        final CompletableFuture<Void> callback = new CompletableFuture<>();

        try {
            PlayerEntityDataStoreEvent event = new PlayerEntityDataStoreEvent(playerUUID, content);
            Freesia.PROXY_SERVER.getPluginManager().callEvent(event);
            Freesia.realPlayerDataStorageManager.save(playerUUID, event.getSerializedNbtData())
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            callback.completeExceptionally(ex);
                            return;
                        }
                        callback.complete(res);
                    });
        } catch (Exception e) {
            callback.completeExceptionally(e);
        }

        return callback;
    }

    @Override
    public void onCommandDispatchResult(int traceId, @Nullable String result) {
        final Consumer<String> removedDecoder = this.pendingCommandDispatches.remove(traceId);

        if (removedDecoder != null) {
            removedDecoder.accept(result);
        }
    }

    @Override
    public void updateWorkerInfo(UUID workerUUID, String workerName) {
        EntryPoint.LOGGER_INST.info("Worker {} (UUID: {}) connected", workerName, workerUUID);

        this.workerName = workerName;
        this.workerUUID = workerUUID;

        Freesia.registedWorkers.put(workerUUID, this);

        Freesia.PROXY_SERVER.getPluginManager().callEvent(new WorkerConnectedEvent(workerUUID, workerName));
    }
}

