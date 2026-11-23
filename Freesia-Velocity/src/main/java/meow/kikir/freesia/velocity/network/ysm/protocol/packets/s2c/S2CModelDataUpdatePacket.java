package meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.YsmPacketProxyBase;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class S2CModelDataUpdatePacket implements YsmPacket {
    private int entityId;
    private byte[] modelData;

    public S2CModelDataUpdatePacket(int entityId, byte[] modelData) {
        this.entityId = entityId;
        this.modelData = modelData;
    }

    public S2CModelDataUpdatePacket() {}

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeVarInt(this.entityId);
        output.writeBytes(this.modelData);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.entityId = input.readVarInt();
        this.modelData = new byte[input.readableBytes()];
        input.readBytes(this.modelData);
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        final YsmPacketProxyBase packetProxy = (YsmPacketProxyBase) handler.getPacketProxy();

        // Check if the packet is current player and drop to prevent incorrect broadcasting
        if (!packetProxy.isEntityStateOfSelf(this.entityId)) {
            return ProxyComputeResult.ofDrop();
        }

        // Update stored data
        packetProxy.setModelDataRaw(this.modelData);
        // Notify updates
        packetProxy.notifyFullTrackerUpdates();

        return ProxyComputeResult.ofDrop();
    }
}
