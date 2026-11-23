package meow.kikir.freesia.common.communicating;

import meow.kikir.freesia.common.communicating.message.IMessage;
import meow.kikir.freesia.common.communicating.message.m2w.M2WDispatchCommandMessage;
import meow.kikir.freesia.common.communicating.message.m2w.M2WFileTransformationPacket;
import meow.kikir.freesia.common.communicating.message.m2w.M2WReadyPacket;
import meow.kikir.freesia.common.communicating.message.w2m.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BuiltinMessageRegistries {
    private static final Map<Integer, Supplier<? extends IMessage<?>>> id2MessageCreators = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IMessage<?>>, Integer> messageClasses2Ids = new ConcurrentHashMap<>();
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    static {
        registerMessage(W2MUpdatePlayerDataRequestMessage.class, W2MUpdatePlayerDataRequestMessage::new);
        registerMessage(W2MCommandResultMessage.class, W2MCommandResultMessage::new);
        registerMessage(M2WDispatchCommandMessage.class, M2WDispatchCommandMessage::new);
        registerMessage(W2MWorkerInfoMessage.class, W2MWorkerInfoMessage::new);
        registerMessage(M2WFileTransformationPacket.class, M2WFileTransformationPacket::new);
        registerMessage(W2MFileTransformationAckPacket.class, W2MFileTransformationAckPacket::new);
        registerMessage(M2WReadyPacket.class, M2WReadyPacket::new);
    }

    public static void registerMessage(Class<? extends IMessage<?>> clazz, Supplier<IMessage<?>> creator) {
        final int packetId = idGenerator.getAndIncrement();

        id2MessageCreators.put(packetId, creator);
        messageClasses2Ids.put(clazz, packetId);
    }


    public static Supplier<? extends IMessage<?>> getMessageCreator(int packetId) {
        return id2MessageCreators.get(packetId);
    }

    public static int getMessageId(Class<IMessage<?>> clazz) {
        return messageClasses2Ids.get(clazz);
    }
}
