package meow.kikir.freesia.velocity;

import meow.kikir.freesia.velocity.network.ysm.protocol.YsmPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s.C2SHandshakeRequestPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s.C2SMolangExecuteRequestPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.c2s.C2SSetPlayAnimation;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CAnimationDataUpdatePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CHandshakeConfirmedPacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CModelDataUpdatePacket;
import meow.kikir.freesia.velocity.network.ysm.protocol.packets.s2c.S2CMolangExecutePacket;
import meow.kikir.freesia.velocity.utils.Pair;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;

public class FreesiaConstants {
    public static final class FileConstants {
        public static final File PLUGINS_DIR = new File("plugins");
        public static final File PLUGIN_DIR = new File(PLUGINS_DIR, "Freesia");

        // config file
        public static final File CONFIG_FILE = new File(PLUGIN_DIR, "freesia_config.toml");

        // player data
        public static final File PLAYER_DATA_DIR = new File(PLUGIN_DIR, "playerdata");
        public static final File VIRTUAL_PLAYER_DATA_DIR = new File(PLUGIN_DIR, "playerdata_virtual");

        public static final String[] MODEL_FOLDERS_SUB = new String[]{
                "auth",
                "custom"
        };
        public static final Path YSM_MODELS_BASE_DIR = Path.of("config", "yes_steve_model");

        static {
            // plugin parent dir
            PLUGIN_DIR.mkdirs();

            // player data
            PLAYER_DATA_DIR.mkdirs();
            VIRTUAL_PLAYER_DATA_DIR.mkdirs();
        }
    }

    public static final class PermissionConstants {
        // permission nodes
        public static final String LIST_PLAYER_COMMAND = "freesia.commands.listysmplayers",
                                   DISPATCH_WORKER_COMMAND = "freesia.commands.dworkerc",
                                   RELOAD_MODELS_COMMAND = "freesia.commands.reloadmodels";
    }

    public static final class LanguageConstants {
        // dworkerc command
        public static final String WORKER_NOT_FOUND = "freesia.worker_command.worker_not_found",
                                   WORKER_COMMAND_FEEDBACK = "freesia.worker_command.command_feedback",

        // listysmplayers command
                                  PLAYER_LIST_HEADER = "freesia.list_player_command_header",
                                  PLAYER_LIST_ENTRY = "freesia.list_player_command_body",

        // handshake detection
                                  HANDSHAKE_TIMED_OUT = "freesia.mod_handshake_time_outed",
        // generic functions
                                  WORKER_TERMINATED_CONNECTION = "freesia.backend.disconnected",
                                  WORKER_NOT_CONNECTED = "freesia.backend.not_connected";

    }

    public static class YsmProtocolMetaConstants {
        // C: Client | S: Server

        // C -> S
        public static final class Serverbound {
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> HAND_SHAKE_REQUEST = new Pair<>("handshake_request", new Pair<>(C2SHandshakeRequestPacket.class, C2SHandshakeRequestPacket::new));
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> MOLANG_EXECUTE_REQ = new Pair<>("molang_execute_req", new Pair<>(C2SMolangExecuteRequestPacket.class, C2SMolangExecuteRequestPacket::new));
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> SET_PLAY_ANIMATION = new Pair<>("set_play_animation", new Pair<>(C2SSetPlayAnimation.class, C2SSetPlayAnimation::new));
        }

        // S -> C
        public static final class Clientbound {
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> HAND_SHAKE_CONFIRMED = new Pair<>("handshake_confirmed", new Pair<>(S2CHandshakeConfirmedPacket.class, S2CHandshakeConfirmedPacket::new));
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> MODEL_DATA_UPDATE = new Pair<>("model_data_update", new Pair<>(S2CModelDataUpdatePacket.class, S2CModelDataUpdatePacket::new));
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> ANIMATION_DATA_UPDATE = new Pair<>("animation_data_update", new Pair<>(S2CAnimationDataUpdatePacket.class, S2CAnimationDataUpdatePacket::new));
            public static final Pair<String, Pair<Class<? extends YsmPacket>, Supplier<YsmPacket>>> MOLANG_EXECUTE = new Pair<>("molang_execute", new Pair<>(S2CMolangExecutePacket.class, S2CMolangExecutePacket::new));
        }
    }
}
