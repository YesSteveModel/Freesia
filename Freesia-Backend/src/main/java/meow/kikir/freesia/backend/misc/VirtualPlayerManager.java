package meow.kikir.freesia.backend.misc;

import io.netty.buffer.Unpooled;
import meow.kikir.freesia.backend.FreesiaBackend;
import meow.kikir.freesia.backend.Utils;
import meow.kikir.freesia.backend.utils.FriendlyByteBuf;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class VirtualPlayerManager implements PluginMessageListener, Listener {
    public static final String CHANNEL_NAME = "freesia:virtual_player_management";

    private final AtomicInteger eventIdGenerator = new AtomicInteger(0);
    private final Map<Integer, Consumer<Boolean>> pendingCallbacks = new ConcurrentHashMap<>();

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] data) {
        if (!channel.equals(CHANNEL_NAME)) {
            return;
        }

        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        final byte packetId = packetBuffer.readByte();

        if (packetId == 2) {
            final int eventId = packetBuffer.readVarInt();
            final boolean result = packetBuffer.readBoolean();

            final Consumer<Boolean> removedCallback = this.pendingCallbacks.remove(eventId);

            if (removedCallback != null) {
                removedCallback.accept(result);
                return;
            }

            FreesiaBackend.INSTANCE.getSLF4JLogger().warn("Received unknown callback for virtual player operations {}", eventId);
        }
    }

    public CompletableFuture<Boolean> setVirtualPlayerData(UUID playerUUID, byte[] data) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(4);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeUUID(playerUUID);
        packetBuffer.writeBytes(data);

        final Player payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.pendingCallbacks.put(generatedEventId, future::complete);

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, packetBuffer.getBytes());
        return future;
    }

    public CompletableFuture<Boolean> removeVirtualPlayer(UUID playerUUID) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(1);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeUUID(playerUUID);

        final Player payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, packetBuffer.getBytes());

        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        this.pendingCallbacks.put(generatedEventId, future::complete);

        return future;
    }

    public CompletableFuture<Boolean> addVirtualPlayer(UUID playerUUID, int entityId) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(0);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeVarInt(entityId);
        packetBuffer.writeUUID(playerUUID);

        final Player payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.pendingCallbacks.put(generatedEventId, future::complete);

        payload.sendPluginMessage(FreesiaBackend.INSTANCE, CHANNEL_NAME, packetBuffer.getBytes());

        return future;
    }
}
