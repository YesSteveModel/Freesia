package com.nguyendevs.freesia.waterfall.network.misc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import io.netty.buffer.Unpooled;
import com.nguyendevs.freesia.waterfall.Freesia;
import com.nguyendevs.freesia.waterfall.utils.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class VirtualPlayerManager implements Listener {
    private static final String MANAGEMENT_CHANNEL_KEY = "freesia:virtual_player_management";

    public void init() {
        Freesia.PROXY_SERVER.registerChannel(MANAGEMENT_CHANNEL_KEY);
        Freesia.PROXY_SERVER.getPluginManager().registerListener(Freesia.INSTANCE, this);
    }

    @EventHandler
    public void onPluginMessage(@NotNull PluginMessageEvent event) {
        Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
            if (!(event.getSender() instanceof Server)) {
                return;
            }

            if (!event.getTag().equals(MANAGEMENT_CHANNEL_KEY)) {
                return;
            }

            event.setCancelled(true);

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

                        ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
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

                        ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
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
                    Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, () -> {
                        final DefaultNBTSerializer serializer = new DefaultNBTSerializer();
                        final NBTCompound deserializedTag;

                        try {
                            deserializedTag = (NBTCompound) serializer.deserializeTag(
                                    NBTLimiter.forBuffer(null, Integer.MAX_VALUE),
                                    new DataInputStream(new ByteArrayInputStream(serializedNbt)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        final Consumer<Boolean> operationCallback = result -> {
                            final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                            response.writeByte(2);
                            response.writeVarInt(eventId);
                            response.writeBoolean(result);

                            ((Server) event.getSender()).sendData(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                        };

                        Freesia.mapperManager.setVirtualPlayerEntityState(virtualPlayerUUID, deserializedTag)
                                .whenComplete((result, ex) -> {
                                    if (ex != null) {
                                        operationCallback.accept(false);
                                        return;
                                    }

                                    operationCallback.accept(result);
                                });
                    });
                }
            }
        });
    }
}

