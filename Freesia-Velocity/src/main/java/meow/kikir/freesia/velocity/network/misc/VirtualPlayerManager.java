package meow.kikir.freesia.velocity.network.misc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class VirtualPlayerManager {
    private static final MinecraftChannelIdentifier MANAGEMENT_CHANNEL_KEY = MinecraftChannelIdentifier.create("freesia", "virtual_player_management");

    public void init() {
        Freesia.PROXY_SERVER.getChannelRegistrar().register(MANAGEMENT_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getEventManager().register(Freesia.INSTANCE, this);
    }

    @Subscribe
    public EventTask onPluginMessage(@NotNull PluginMessageEvent event) {
        return EventTask.async(() -> {
            if (!(event.getSource() instanceof ServerConnection)) {
                return;
            }

            if (!event.getIdentifier().getId().equals(MANAGEMENT_CHANNEL_KEY.getId())) {
                return;
            }

            event.setResult(PluginMessageEvent.ForwardResult.handled());

            final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

            switch (packetData.readByte()) {
                case 0 -> { // Create virtual player packet
                    final int eventId = packetData.readVarInt();
                    final int entityId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final Consumer<Boolean> operationCallback = result -> {
                        final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                        response.writeByte(2);
                        response.writeVarInt(eventId);
                        response.writeBoolean(result);

                        ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.addVirtualPlayer(virtualPlayerUUID, entityId).whenComplete((result, ex) -> {
                        if (ex != null) {
                            operationCallback.accept(false);
                            return;
                        }

                        operationCallback.accept(result);
                    });
                }

                case 1 -> { // Remove virtual player packet
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final Consumer<Boolean> operationCallback = result -> {
                        final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                        response.writeByte(2);
                        response.writeVarInt(eventId);
                        response.writeBoolean(result);

                        ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                    };

                    Freesia.mapperManager.removeVirtualPlayer(virtualPlayerUUID).whenComplete((result, ex) -> {
                        if (ex != null) {
                            operationCallback.accept(false);
                            return;
                        }

                        operationCallback.accept(result);
                    });
                }

                case 4 -> {
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();
                    final byte[] serializedNbt = new byte[packetData.readableBytes()];
                    packetData.readBytes(serializedNbt);

                    // Async io
                    Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, () -> {
                        final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                        final NBTCompound deserializedTag;

                        try {
                            deserializedTag = (NBTCompound) serializer.deserializeTag(NBTLimiter.forBuffer(null, Integer.MAX_VALUE), new DataInputStream(new ByteArrayInputStream(serializedNbt)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        final Consumer<Boolean> operationCallback = result -> {
                            final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                            response.writeByte(2);
                            response.writeVarInt(eventId);
                            response.writeBoolean(result);

                            ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                        };

                        Freesia.mapperManager.setVirtualPlayerEntityState(virtualPlayerUUID, deserializedTag).whenComplete((result ,ex) -> {
                            if (ex != null) {
                                operationCallback.accept(false);
                                return;
                            }

                            operationCallback.accept(result);
                        });
                    }).schedule();
                }
            }
        });
    }
}
