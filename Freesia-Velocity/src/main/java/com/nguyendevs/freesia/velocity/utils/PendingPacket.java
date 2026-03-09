package com.nguyendevs.freesia.velocity.utils;

import net.kyori.adventure.key.Key;

/**
 * Pending packet object for callback processing
 * @see com.nguyendevs.freesia.velocity.network.ysm.MapperSessionProcessor
 * @param channel Channel name of the packet
 * @param data Data of the packet
 */
public record PendingPacket(
        Key channel,
        byte[] data
) {
}

