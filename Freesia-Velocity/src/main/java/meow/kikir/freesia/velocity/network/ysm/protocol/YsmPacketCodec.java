package meow.kikir.freesia.velocity.network.ysm.protocol;

import com.google.common.collect.Maps;
import io.netty.buffer.Unpooled;
import meow.kikir.freesia.velocity.Freesia;
import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.YsmProtocolMetaFile;
import meow.kikir.freesia.common.utils.SimpleFriendlyByteBuf;
import meow.kikir.freesia.velocity.utils.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

public class YsmPacketCodec {
    public static final YsmPacketRegistry PACKET_REGISTRY = new YsmPacketRegistry();
    static {
        try {
            for (Field field : FreesiaConstants.YsmProtocolMetaConstants.Clientbound.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() != Pair.class) {
                    continue;
                }

                final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> packetInfo = (Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>>) field.get(null);
                final String propertyName = packetInfo.first();
                final Class<? extends YsmPacket> packetClass = packetInfo.second().first();
                final Supplier<YsmPacket> constructor = packetInfo.second().second();

                final int packetId = YsmProtocolMetaFile.getS2CPacketId(propertyName);

                if (!PACKET_REGISTRY.register(EnumPacketDirection.S2C, packetId, packetClass, constructor)) {
                    Freesia.LOGGER.warn("Duplicate registration of S2C packet id {} for class {}", packetId, packetClass.getName());
                    continue;
                }

                Freesia.LOGGER.info("Registered S2C packet id {} for class {}", packetId, packetClass.getName());
            }

            for (Field field : FreesiaConstants.YsmProtocolMetaConstants.Serverbound.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() != Pair.class) {
                    continue;
                }

                final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> packetInfo = (Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>>) field.get(null);
                final String propertyName = packetInfo.first();
                final Class<? extends YsmPacket> packetClass = packetInfo.second().first();
                final Supplier<YsmPacket> constructor = packetInfo.second().second();

                final int packetId = YsmProtocolMetaFile.getC2SPacketId(propertyName);

                if (!PACKET_REGISTRY.register(EnumPacketDirection.C2S, packetId, packetClass, constructor)) {
                    Freesia.LOGGER.warn("Duplicate registration of C2S packet id {} for class {}", packetId, packetClass.getName());
                    continue;
                }

                Freesia.LOGGER.info("Registered C2S packet id {} for class {}", packetId, packetClass.getName());
            }
        }catch (Exception e) {
            throw new RuntimeException("Failed to initialize YsmPacketCodec", e);
        }
    }

    public static final class YsmPacketRegistry {
        private final Map<Integer, Supplier<YsmPacket>> c2sRegistry = Maps.newLinkedHashMap();
        private final Map<Integer, Supplier<YsmPacket>> s2cRegistry = Maps.newLinkedHashMap();

        private final Map<Class<? extends YsmPacket>, Integer> reverseRegistry = Maps.newLinkedHashMap();

        public Map<Integer, Supplier<YsmPacket>> getRegistry(@NotNull EnumPacketDirection direction) {
            return switch (direction) {
                case C2S -> this.c2sRegistry;
                case S2C -> this.s2cRegistry;
            };
        }

        public boolean register(EnumPacketDirection direction, int packetId, Class<? extends YsmPacket> packetClazz, Supplier<YsmPacket> constructor) {
            final Map<Integer, Supplier<YsmPacket>> registry = this.getRegistry(direction);

            if (registry.containsKey(packetId)) {
                return false;
            }

            registry.put(packetId, constructor);
            this.reverseRegistry.put(packetClazz, packetId);
            return true;
        }

        public byte packetIdOf(@NotNull YsmPacket packet) {
            final Integer packetId = this.reverseRegistry.get(packet.getClass());
            if (packetId == null) {
                throw new IllegalArgumentException("Unregistered packet class: " + packet.getClass());
            }

            return packetId.byteValue();
        }
    }

    public static @Nullable YsmPacket tryDecode(@NotNull SimpleFriendlyByteBuf input, EnumPacketDirection direction) {
        final int readerIndexBefore = input.readerIndex();
        final int packetId = input.readByte();

        final Supplier<YsmPacket> constructor = PACKET_REGISTRY.getRegistry(direction).get(packetId);
        if (constructor == null) {
            input.readerIndex(readerIndexBefore);
            return null;
        }

        final YsmPacket packet = constructor.get();
        packet.decode(input);
        input.readerIndex(readerIndexBefore); // still need to reset reader index for proxy processing
        return packet;
    }

    public static @NotNull SimpleFriendlyByteBuf encode(YsmPacket packet){
        final SimpleFriendlyByteBuf result = new SimpleFriendlyByteBuf(Unpooled.buffer());
        final byte packetId = PACKET_REGISTRY.packetIdOf(packet);
        result.writeByte(packetId);
        packet.encode(result);
        return result;
    }
}
