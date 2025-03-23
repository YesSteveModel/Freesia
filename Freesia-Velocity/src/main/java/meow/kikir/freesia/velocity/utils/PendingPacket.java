package meow.kikir.freesia.velocity.utils;

import net.kyori.adventure.key.Key;

public record PendingPacket(
        Key channel,
        byte[] data
) {
}
