package meow.kikir.freesia.waterfall.utils;

import net.kyori.adventure.key.Key;

/**
 * Pending packet object for callback processing
 * @see meow.kikir.freesia.waterfall.network.ysm.MapperSessionProcessor
 * @param channel Channel name of the packet
 * @param data Data of the packet
 */
public record PendingPacket(
        Key channel,
        byte[] data
) {
}
