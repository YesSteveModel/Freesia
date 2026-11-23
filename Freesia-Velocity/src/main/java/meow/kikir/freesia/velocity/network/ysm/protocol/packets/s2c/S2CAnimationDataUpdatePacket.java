package meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.YsmPacketProxyBase;
import meow.kikir.freesia.velocity.network.ysm.protocol.EntityIdRemappablePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class S2CAnimationDataUpdatePacket implements YsmPacket, EntityIdRemappablePacket {
    private int entityId;
    private byte[] animationData;

    public S2CAnimationDataUpdatePacket(int entityId, byte[] animationData) {
        this.entityId = entityId;
        this.animationData = animationData;
    }

    public S2CAnimationDataUpdatePacket() {}

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeVarInt(this.entityId);
        output.writeBytes(this.animationData);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.entityId = input.readVarInt();
        this.animationData = new byte[input.readableBytes()];
        input.readBytes(this.animationData);
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        final YsmPacketProxyBase packetProxy = (YsmPacketProxyBase) handler.getPacketProxy();

        // Check if the packet is current player and drop to prevent incorrect broadcasting
        if (!packetProxy.isEntityStateOfSelf(this.entityId)) {
            return ProxyComputeResult.ofDrop();
        }

        // Update stored data
        packetProxy.setAnimationDataRaw(this.animationData);

        packetProxy.notifyFullTrackerUpdates(); // Notify updates

        return ProxyComputeResult.ofDrop();
    }
}
