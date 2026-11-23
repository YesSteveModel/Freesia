package meow.kikir.freesia.velocity.network.ysm.protocol;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import meow.kikir.freesia.velocity.network.ysm.ProxyComputeResult;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public interface YsmPacket {
    void encode(@NotNull SimpleFriendlyByteBuf output);

    void decode(@NotNull SimpleFriendlyByteBuf input);

    ProxyComputeResult handle(@NotNull MapperConnectionHandler handler);
}
