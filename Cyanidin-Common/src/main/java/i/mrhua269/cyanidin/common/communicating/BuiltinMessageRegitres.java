package i.mrhua269.cyanidin.common.communicating;

import i.mrhua269.cyanidin.common.communicating.message.IMessage;
import i.mrhua269.cyanidin.common.communicating.message.m2w.M2WPlayerDataResponseMessage;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MPlayerDataGetRequestMessage;
import i.mrhua269.cyanidin.common.communicating.message.w2m.W2MUpdatePlayerDataRequestMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BuiltinMessageRegitres {
    private static final Map<Integer, Supplier<? extends IMessage>> id2MessageCreators = new ConcurrentHashMap<>();
    private static final Map<Class<? extends IMessage>, Integer> messageClasses2Ids = new ConcurrentHashMap<>();
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    static {
        registerMessage(W2MPlayerDataGetRequestMessage.class, W2MPlayerDataGetRequestMessage::new);
        registerMessage(W2MUpdatePlayerDataRequestMessage.class, W2MUpdatePlayerDataRequestMessage::new);
        registerMessage(M2WPlayerDataResponseMessage.class, M2WPlayerDataResponseMessage::new);
    }

    public static void registerMessage(Class<? extends IMessage> clazz, Supplier<IMessage> creator){
        final int packetId = idGenerator.getAndIncrement();

        id2MessageCreators.put(packetId, creator);
        messageClasses2Ids.put(clazz, packetId);
    }


    public static Supplier<? extends IMessage> getMessageCreator(int packetId){
        return id2MessageCreators.get(packetId);
    }

    public static int getMessageId(Class<? extends IMessage> clazz){
        return messageClasses2Ids.get(clazz);
    }
}
