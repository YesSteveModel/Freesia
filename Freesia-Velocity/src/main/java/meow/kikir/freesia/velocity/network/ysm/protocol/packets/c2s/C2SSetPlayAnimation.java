package meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.EntityIdRemappablePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class C2SSetPlayAnimation implements YsmPacket, EntityIdRemappablePacket {
    private int extraAnimationIndex;
    private String classifyId;
    private int entityId;

    public C2SSetPlayAnimation() {}

    public C2SSetPlayAnimation(int entityId, String classifyId, int extraAnimationIndex) {
        this.entityId = entityId;
        this.classifyId = classifyId;
        this.extraAnimationIndex = extraAnimationIndex;
    }

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeVarInt(this.extraAnimationIndex);
        output.writeUtf(this.classifyId);
        output.writeVarInt(this.entityId);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.extraAnimationIndex = input.readVarInt();
        this.classifyId = input.readUtf();
        this.entityId = input.readVarInt();
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        return ProxyComputeResult.ofPass();
    }
}
