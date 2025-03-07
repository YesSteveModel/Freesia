package meow.kikir.freesia.velocity;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.net.InetSocketAddress;

public class FreesiaConfig {
    public static InetSocketAddress workerMSessionAddress = new InetSocketAddress("127.0.0.1", 19199);
    public static InetSocketAddress masterServiceAddress = new InetSocketAddress("127.0.0.1", 19200);
    public static String languageName = "zh_CN";
    public static boolean kickIfYsmNotInstalled = false;
    public static int ysmDetectionTimeout = 3000;

    private static CommentedFileConfig CONFIG_INSTANCE;

    private static void loadOrDefaultValues() {
        workerMSessionAddress = new InetSocketAddress(
                get("worker.worker_msession_ip", workerMSessionAddress.getHostName()),
                get("worker.worker_msession_port", workerMSessionAddress.getPort())
        );
        masterServiceAddress = new InetSocketAddress(
                get("worker.worker_master_ip", masterServiceAddress.getHostName()),
                get("worker.worker_master_port", masterServiceAddress.getPort())
        );
        languageName = get("messages.language", languageName);

        kickIfYsmNotInstalled = get("functions.kick_if_ysm_not_installed", kickIfYsmNotInstalled);
        ysmDetectionTimeout = get("functions.ysm_detection_timeout_for_kicking", ysmDetectionTimeout);
    }

    private static <T> T get(String key, T def) {
        if (!CONFIG_INSTANCE.contains(key)) {
            CONFIG_INSTANCE.add(key, def);
            return def;
        }

        return CONFIG_INSTANCE.get(key);
    }

    public static void init() throws IOException {
        Freesia.LOGGER.info("Loading config file.");

        if (!FreesiaConstants.FileConstants.CONFIG_FILE.exists()) {
            Freesia.LOGGER.info("Config file not found! Creating new config file.");
            FreesiaConstants.FileConstants. CONFIG_FILE.createNewFile();
        }

        CONFIG_INSTANCE = CommentedFileConfig.ofConcurrent(FreesiaConstants.FileConstants.CONFIG_FILE);

        CONFIG_INSTANCE.load();

        try {
            loadOrDefaultValues();
        } catch (Exception e) {
            Freesia.LOGGER.error("Failed to load config file!", e);
        }

        CONFIG_INSTANCE.save();
    }
}
