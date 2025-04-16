package meow.kikir.freesia.velocity.utils;

import net.kyori.adventure.key.Key;

/**
 * Pending packet object for callback processing
 * @see meow.kikir.freesia.velocity.network.ysm.MapperSessionProcessor
 * @param channel Channel name of the packet
 * @param data Data of the packet
 */
public record PendingPacket(
        Key channel,
        byte[] data
) {
}
