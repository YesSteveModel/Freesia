package meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.EntityIdRemappablePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CMolangExecutePacket;
import org.jetbrains.annotations.NotNull;

public class C2SMolangExecuteRequestPacket implements YsmPacket, EntityIdRemappablePacket {
    private int entityId;
    private String expression;

    public C2SMolangExecuteRequestPacket() {}

    public C2SMolangExecuteRequestPacket(int entityId, String expression) {
        this.entityId = entityId;
        this.expression = expression;
    }

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeUtf(this.expression);
        output.writeVarInt(this.entityId);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.expression = input.readUtf();
        this.entityId = input.readVarInt();
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        final S2CMolangExecutePacket response = new S2CMolangExecutePacket(new int[]{this.entityId}, this.expression);

        handler.getPacketProxy().sendYsmPacket(response);

        return ProxyComputeResult.ofDrop();
    }
}
