package meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.velocity.network.ysm.protocol.EntityIdRemappablePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class S2CMolangExecutePacket implements YsmPacket, EntityIdRemappablePacket {
    private int[] entityIds;
    private String expression;

    public S2CMolangExecutePacket(int[] entityIds, String expression) {
        this.entityIds = entityIds;
        this.expression = expression;
    }

    public S2CMolangExecutePacket() {}

    @Override
    public void encode(@NotNull SimpleFriendlyByteBuf output) {
        output.writeVarIntArray(this.entityIds);
        output.writeUtf(this.expression);
    }

    @Override
    public void decode(@NotNull SimpleFriendlyByteBuf input) {
        this.entityIds = input.readVarIntArray();
        this.expression = input.readUtf();
    }

    @Override
    public ProxyComputeResult handle(@NotNull MapperConnectionHandler handler) {
        return ProxyComputeResult.ofPass();
    }
}
