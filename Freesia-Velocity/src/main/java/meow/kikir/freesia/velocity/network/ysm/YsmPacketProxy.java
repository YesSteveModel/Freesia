package meow.kikir.freesia.velocity.network.ysm;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;


public interface YsmPacketProxy {
    ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData);

    ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData);

    void blockUntilProxyReady();

    Player getOwner();

    void sendEntityStateTo(@NotNull Player target);

    void setEntityDataRaw(NBTCompound data);

    void refreshToOthers();

    NBTCompound getCurrentEntityState();

    default void sendPluginMessageToOwner(@NotNull MinecraftChannelIdentifier channel, byte[] data) {
        this.sendPluginMessageTo(this.getOwner(), channel, data);
    }

    default void sendPluginMessageToOwner(@NotNull MinecraftChannelIdentifier channel, @NotNull ByteBuf data) {
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageToOwner(channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull Player target, @NotNull MinecraftChannelIdentifier channel, @NotNull ByteBuf data) {
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageTo(target, channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull Player target, @NotNull MinecraftChannelIdentifier channel, byte[] data) {
        target.sendPluginMessage(channel, data);
    }
}
