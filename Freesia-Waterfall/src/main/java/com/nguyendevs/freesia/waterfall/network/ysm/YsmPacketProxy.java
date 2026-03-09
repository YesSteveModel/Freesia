package com.nguyendevs.freesia.waterfall.network.ysm;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface YsmPacketProxy {
    default void setParentHandler(MapperSessionProcessor processor) {
        // No-op by default
    }

    ProxyComputeResult processS2C(Key channelKey, ByteBuf copiedPacketData);

    ProxyComputeResult processC2S(Key channelKey, ByteBuf copiedPacketData);

    @Nullable
    ProxiedPlayer getOwner();

    void sendEntityStateTo(@NotNull ProxiedPlayer target);

    void setEntityDataRaw(YsmState data);

    void notifyFullTrackerUpdates();

    YsmState getCurrentEntityState();

    void setPlayerWorkerEntityId(int id);

    void setPlayerEntityId(int id);

    int getPlayerEntityId();

    int getPlayerWorkerEntityId();

    default void executeMolang(String expression) {
    }

    default void executeMolang(int[] entityIds, String expression) {
    }

    default void sendPluginMessageToOwner(@NotNull String channel, byte[] data) {
        final ProxiedPlayer owner = this.getOwner();

        if (owner == null) {
            throw new UnsupportedOperationException();
        }

        this.sendPluginMessageTo(this.getOwner(), channel, data);
    }

    default void sendPluginMessageToOwner(@NotNull String channel, @NotNull ByteBuf data) {
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageToOwner(channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull ProxiedPlayer target, @NotNull String channel, @NotNull ByteBuf data) {
        final byte[] dataArray = new byte[data.readableBytes()];
        data.readBytes(dataArray);

        this.sendPluginMessageTo(target, channel, dataArray);
    }

    default void sendPluginMessageTo(@NotNull ProxiedPlayer target, @NotNull String channel, byte[] data) {
        target.sendData(channel, data);
    }
}

