package meow.kikir.freesia.velocity.network.backend;

import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import meow.kikir.freesia.common.communicating.message.m2w.M2WDispatchCommandMessage;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.events.PlayerEntityDataLoadEvent;
import meow.kikir.freesia.velocity.events.PlayerEntityDataStoreEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MasterServerMessageHandler extends NettyServerChannelHandlerLayer {
    private final Map<Integer, Consumer<String>> pendingCommandDispatches = Maps.newConcurrentMap();
    private final AtomicInteger traceIdGenerator = new AtomicInteger(0);
    private volatile UUID workerUUID;
    private volatile String workerName;

    public void dispatchCommandToWorker(String command, Consumer<Component> onDispatched) {
        final int traceId = this.traceIdGenerator.getAndIncrement();
        final Consumer<String> wrappedDecoder = json -> {
            try {
                final Component decoded = LegacyComponentSerializer.builder().build().deserialize(json);
                onDispatched.accept(decoded);
            } catch (Exception e) {
                EntryPoint.LOGGER_INST.error("Failed to decode command result from worker", e);
            }
        };

        this.pendingCommandDispatches.put(traceId, wrappedDecoder);
        this.sendMessage(new M2WDispatchCommandMessage(traceId, command));
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
        if (this.workerUUID == null) {
            return;
        }

        Freesia.registedWorkers.remove(this.workerUUID);
    }

    @Override
    public CompletableFuture<byte[]> readPlayerData(UUID playerUUID) {
        final CompletableFuture<byte[]> callback = new CompletableFuture<>();
        Freesia.realPlayerDataStorageManager
                .loadPlayerData(playerUUID)
                .thenApply(data -> Freesia.PROXY_SERVER
                        .getEventManager()
                        .fire(new PlayerEntityDataLoadEvent(playerUUID, data))
                        .thenApply(PlayerEntityDataLoadEvent::getSerializedNbtData)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                callback.completeExceptionally(ex);
                                return;
                            }

                            callback.complete(result);
                        })
                );
        return callback;
    }

    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content) {
        return Freesia.PROXY_SERVER
                .getEventManager()
                .fire(new PlayerEntityDataStoreEvent(playerUUID, content))
                .thenAccept(event -> Freesia.realPlayerDataStorageManager.save(playerUUID, event.getSerializedNbtData()));
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
    }
}
