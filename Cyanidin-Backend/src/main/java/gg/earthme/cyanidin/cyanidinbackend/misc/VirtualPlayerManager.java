package gg.earthme.cyanidin.cyanidinbackend.misc;

import gg.earthme.cyanidin.cyanidinbackend.CyanidinBackend;
import gg.earthme.cyanidin.cyanidinbackend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VirtualPlayerManager implements PluginMessageListener, Listener {
    private static final String CHANNEL_NAME = "cyanidin:virtual_player_management";
    private final Set<UUID> managedVirtualPlayers = new HashSet<>();

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte @NotNull [] data) {
    }

    public boolean removeVirtualPlayer(UUID playerUUID, Player payload) {
        if (!this.managedVirtualPlayers.remove(playerUUID)){
            return false;
        }


        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());

        packetBuffer.writeByte(1);
        packetBuffer.writeUUID(playerUUID);

        payload.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, packetBuffer.array());
        return true;
    }

    public boolean addVirtualPlayer(UUID playerUUID, int entityId, Player payload) {
        if (this.managedVirtualPlayers.contains(playerUUID)){
            return false;
        }

        this.managedVirtualPlayers.add(playerUUID);

        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());

        packetBuffer.writeByte(0);
        packetBuffer.writeVarInt(entityId);
        packetBuffer.writeUUID(playerUUID);

        payload.sendPluginMessage(CyanidinBackend.INSTANCE, CHANNEL_NAME, packetBuffer.array());
    }
}
