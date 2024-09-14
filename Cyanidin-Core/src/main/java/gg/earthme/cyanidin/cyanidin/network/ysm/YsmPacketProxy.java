package gg.earthme.cyanidin.cyanidin.network.ysm;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;


public interface YsmPacketProxy {
    ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData);

    ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData);

    void blockUntilProxyReady();

    Player getOwner();

    default void sendPluginMessageToOwner(@NotNull NamespacedKey channel, byte[] data){
        this.sendPluginMessageTo(this.getOwner(), channel, data);
    }

    default void sendPluginMessageToOwner(@NotNull NamespacedKey channel, @NotNull ByteBuf data){
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageToOwner(channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull Player target, @NotNull NamespacedKey channel, @NotNull ByteBuf data){
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageTo(target, channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull Player target, @NotNull NamespacedKey channel, byte[] data){
        PacketEvents.getAPI().getPlayerManager().getUser(target).sendPacket(new WrapperPlayServerPluginMessage(channel.toString(), data));
    }
}
