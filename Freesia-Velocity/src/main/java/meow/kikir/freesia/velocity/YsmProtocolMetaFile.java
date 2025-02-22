package meow.kikir.freesia.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class YsmProtocolMetaFile {
    private static final Properties INTERNAL_PROPERTIES = new Properties();

    static {
        final InputStream protocolPropertiesInputstream = YsmProtocolMetaFile.class.getClassLoader().getResourceAsStream("ysm_protocol.properties");

        if (protocolPropertiesInputstream == null) throw new RuntimeException("Ysm protocol meta loss!");

        try {
            INTERNAL_PROPERTIES.load(protocolPropertiesInputstream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getYsmChannelPath() {
        return INTERNAL_PROPERTIES.getProperty("channel_path");
    }

    public static String getYsmChannelNamespace() {
        return INTERNAL_PROPERTIES.getProperty("channel_namespace");
    }

    public static int getC2SPacketId(String type) {
        return Integer.parseInt(INTERNAL_PROPERTIES.getProperty("clientbound_" + type + "_pkt_id"));
    }

    public static int getS2CPacketId(String type) {
        return Integer.parseInt(INTERNAL_PROPERTIES.getProperty("serverbound_" + type + "_pkt_id"));
    }

    public static class ProtocolKeys {
        public static class Serverbound{
            public static final String HAND_SHAKE_REQUEST = "handshake_request";
        }

        public static class Clientbound {
            public static final String HAND_SHAKE_CONFIRMED = "handshake_confirmed";

            public static final String ENTITY_DATA_UPDATE = "entity_data_update";
        }
    }
}
