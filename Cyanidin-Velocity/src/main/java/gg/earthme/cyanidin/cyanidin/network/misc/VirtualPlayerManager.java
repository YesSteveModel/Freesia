package gg.earthme.cyanidin.cyanidin.network.misc;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import gg.earthme.cyanidin.cyanidin.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VirtualPlayerManager {
    private static final MinecraftChannelIdentifier MANAGEMENT_CHANNEL_KEY = MinecraftChannelIdentifier.create("cyanidin", "virtual_player_management");

    public void init(){
        Cyanidin.PROXY_SERVER.getChannelRegistrar().register(MANAGEMENT_CHANNEL_KEY);
        Cyanidin.PROXY_SERVER.getEventManager().register(Cyanidin.INSTANCE, this);
    }

    @Subscribe
    public EventTask onPluginMessage(@NotNull PluginMessageEvent event){
        return EventTask.async(() -> {
            if (!(event.getSource() instanceof ServerConnection)){
                return;
            }

            if (!event.getIdentifier().getId().equals(MANAGEMENT_CHANNEL_KEY.getId())){
                return;
            }

            event.setResult(PluginMessageEvent.ForwardResult.handled());

            final FriendlyByteBuf packetData = new FriendlyByteBuf(Unpooled.wrappedBuffer(event.getData()));

            switch (packetData.readByte()){
                case 0 -> { // Create virtual player packet
                    final int eventId = packetData.readVarInt();
                    final int entityId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final boolean result = Cyanidin.mapperManager.addVirtualPlayer(virtualPlayerUUID, entityId);

                    final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                    response.writeByte(2);
                    response.writeVarInt(eventId);
                    response.writeBoolean(result);

                    ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                }

                case 1 -> { // Remove virtual player packet
                    final int eventId = packetData.readVarInt();
                    final UUID virtualPlayerUUID = packetData.readUUID();

                    final boolean result = Cyanidin.mapperManager.removeVirtualPlayer(virtualPlayerUUID);

                    final FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
                    response.writeByte(3);
                    response.writeVarInt(eventId);
                    response.writeBoolean(result);

                    ((ServerConnection) event.getSource()).sendPluginMessage(MANAGEMENT_CHANNEL_KEY, response.getBytes());
                }
            }
        });
    }
}
