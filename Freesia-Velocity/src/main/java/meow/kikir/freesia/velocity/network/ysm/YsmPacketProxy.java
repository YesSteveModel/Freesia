package meow.kikir.freesia.velocity.network.ysm;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.netty.buffer.ByteBuf;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacketCodec;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface YsmPacketProxy {
    void setParentHandler(MapperConnectionHandler processor);

    ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData);

    ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData);

    @Nullable
    Player getOwner();

    void sendFullEntityDataTo(@NotNull Player target);

    void setModelDataRaw(byte[] data);

    byte[] getCurrentModelData();

    void setAnimationDataRaw(byte[] data);

    byte[] getCurrentAnimationDataRaw(byte[] data);

    void notifyFullTrackerUpdates();

    void setPlayerWorkerEntityId(int id);

    void setPlayerEntityId(int id);

    int getPlayerEntityId();

    int getPlayerWorkerEntityId();

    default void executeMolang(String expression) {}

    default void executeMolang(int[] entityIds, String expression) {}

    default void onProxyReadyCallback() {}


    /*
    下面的都是方便从 velocity 向玩家发包用的了(x)
     */
    default void sendYsmPacket(YsmPacket packet) {
        final Player owner = this.getOwner();

        if (owner == null) {
            throw new UnsupportedOperationException();
        }

        this.sendYsmPacket(owner, packet);
    }

    default void sendYsmPacket(Player receiver, YsmPacket packet) {
        if (receiver == null) {
            throw new UnsupportedOperationException();
        }

        final SimpleFriendlyByteBuf encoded = YsmPacketCodec.encode(packet);
        this.sendPluginMessageTo(receiver, MappersManager.YSM_CHANNEL_KEY_VELOCITY, encoded);
    }

    default void sendPluginMessageToOwner(@NotNull MinecraftChannelIdentifier channel, byte[] data){
        final Player owner = this.getOwner();

        if (owner == null) {
            throw new UnsupportedOperationException();
        }

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
