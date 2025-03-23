package meow.kikir.freesia.worker;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

public class FreesiaWorkerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final File CONFIG_FILE_DIR = new File("config");
    private static final File CONFIG_FILE = new File(CONFIG_FILE_DIR, "freesia_config.toml");
    public static InetSocketAddress masterServiceAddress = new InetSocketAddress("127.0.0.1", 19200);
    public static int reconnectInterval = 1;
    public static int playerDataCacheInvalidateIntervalSeconds = 30;
    private static CommentedFileConfig CONFIG_INSTANCE;

    static {
        CONFIG_FILE_DIR.mkdirs();
    }

    private static void loadOrDefaultValues() {
        masterServiceAddress = new InetSocketAddress(
                get("worker.worker_master_ip", masterServiceAddress.getHostName()),
                get("worker.worker_master_port", masterServiceAddress.getPort())
        );
        reconnectInterval = get("worker.controller_reconnect_interval", reconnectInterval);
        playerDataCacheInvalidateIntervalSeconds = get("worker.player_data_cache_invalidate_interval_seconds", playerDataCacheInvalidateIntervalSeconds);
    }

    private static <T> T get(String key, T def) {
        if (!CONFIG_INSTANCE.contains(key)) {
            CONFIG_INSTANCE.add(key, def);
            return def;
        }

        return CONFIG_INSTANCE.get(key);
    }

    public static void init() throws IOException {
        LOGGER.info("Loading config file.");

        if (!CONFIG_FILE.exists()) {
            LOGGER.info("Config file not found! Creating new config file.");
            CONFIG_FILE.createNewFile();
        }

        CONFIG_INSTANCE = CommentedFileConfig.ofConcurrent(CONFIG_FILE);

        CONFIG_INSTANCE.load();

        try {
            loadOrDefaultValues();
        } catch (Exception e) {
            LOGGER.error("Failed to load config file!", e);
        }

        CONFIG_INSTANCE.save();
    }
}
