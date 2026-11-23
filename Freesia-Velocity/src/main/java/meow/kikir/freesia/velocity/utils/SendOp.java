package meow.kikir.freesia.velocity.utils;

import meow.kikir.freesia.velocity.network.ysm.MapperConnectionHandler;
import net.kyori.adventure.key.Key;

/**
 * Pending packet sending operation object for callback processing
 * @see MapperConnectionHandler
 * @param channel Channel name of the packet
 * @param data Data of the packet
 */
public record SendOp(
        Key channel,
        byte[] data
) {
}
