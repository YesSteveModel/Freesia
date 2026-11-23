package meow.kikir.freesia.common.data;

import io.netty.buffer.Unpooled;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RequestedPlayerData {
    private UUID requestedPlayerUUID;
    private int entityId;
    private byte[] ysmNbtData;

    public RequestedPlayerData(UUID requestedPlayerUUID, int entityId, byte[] ysmNbtData) {
        this.requestedPlayerUUID = requestedPlayerUUID;
        this.entityId = entityId;
        this.ysmNbtData = ysmNbtData;
    }

    public RequestedPlayerData() { }

    public SimpleFriendlyByteBuf encode() {
        SimpleFriendlyByteBuf buf = new SimpleFriendlyByteBuf(Unpooled.buffer());

        buf.writeUUID(requestedPlayerUUID);
        buf.writeInt(entityId);


        if (ysmNbtData == null)
            buf.writeInt(0);
        else {
            buf.writeInt(ysmNbtData.length);
            buf.writeBytes(ysmNbtData);
        }

        return buf;
    }

    public void restoreFrom(@NotNull SimpleFriendlyByteBuf buf) {
        this.requestedPlayerUUID = buf.readUUID();
        this.entityId = buf.readInt();

        final int ysmNbtDataLen = buf.readInt();
        if (ysmNbtDataLen <= 0) {
            return;
        }

        this.ysmNbtData = new byte[ysmNbtDataLen];
        buf.readBytes(this.ysmNbtData);
    }

    public UUID getRequestedPlayerUUID() {
        return this.requestedPlayerUUID;
    }

    public byte[] getYsmNbtData() {
        return this.ysmNbtData;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public void setRequestedPlayerUUID(UUID requestedPlayerUUID) {
        this.requestedPlayerUUID = requestedPlayerUUID;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public void setYsmNbtData(byte[] ysmNbtData) {
        this.ysmNbtData = ysmNbtData;
    }

    public static @NotNull RequestedPlayerData from(SimpleFriendlyByteBuf buf) {
        RequestedPlayerData data = new RequestedPlayerData();

        data.restoreFrom(buf);

        return data;
    }
}
