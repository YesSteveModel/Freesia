package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.communicating.message.IMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BuiltinMessageRegitres {
    private static final Map<Integer, Supplier<IMessage>> s2cMessages = new ConcurrentHashMap<>();
    private static final Map<Integer, Supplier<IMessage>> c2sMessages = new ConcurrentHashMap<>();

    private static final Map<Class<? extends IMessage>, Integer> messageClasses2Ids = new ConcurrentHashMap<>();
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    public static void registerS2CMessage(Class<? extends IMessage> clazz, Supplier<IMessage> creator){
        final int packetId = idGenerator.getAndIncrement();

        s2cMessages.put(packetId, creator);
        messageClasses2Ids.put(clazz, packetId);
    }

    public static void registerC2SMessage(Class<? extends IMessage> clazz, Supplier<IMessage> creator){
        final int packetId = idGenerator.getAndIncrement();

        c2sMessages.put(packetId, creator);
        messageClasses2Ids.put(clazz, packetId);
    }

    public static Supplier<IMessage> getS2CMessageCreator(int packetId){
        return s2cMessages.get(packetId);
    }

    public static Supplier<IMessage> getC2SMessageCreator(int packetId){
        return c2sMessages.get(packetId);
    }

    public static int getMessageId(Class<? extends IMessage> clazz){
        return messageClasses2Ids.get(clazz);
    }
}
