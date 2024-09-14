package gg.earthme.cyanidin.cyanidin;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

public class CyanidinConfig {
    private static final File PLUGIN_DIR = new File("plugins");
    private static final File CONFIG_FILE_DIR = new File(PLUGIN_DIR, "Cyanidin");
    private static final File CONFIG_FILE = new File(CONFIG_FILE_DIR, "cyanidin_config.toml");
    private static CommentedFileConfig CONFIG_INSTANCE;

    public static InetSocketAddress workerMSessionAddress = new InetSocketAddress("127.0.0.1", 19199);
    public static InetSocketAddress masterServiceAddress = new InetSocketAddress("127.0.0.1", 19200);
    public static String languageName = "zh_CN";

    static {
        CONFIG_FILE_DIR.mkdirs();
    }

    private static void loadOrDefaultValues(){
        workerMSessionAddress = new InetSocketAddress(
                get("worker.worker_msession_ip", workerMSessionAddress.getHostName()),
                get("worker.worker_msession_port", workerMSessionAddress.getPort())
        );
        masterServiceAddress = new InetSocketAddress(
                get("worker.worker_master_ip", masterServiceAddress.getHostName()),
                get("worker.worker_master_port", masterServiceAddress.getPort())
        );
        languageName = get("messages.language", languageName);
    }

    private static <T> T get(String key, T def){
        if (!CONFIG_INSTANCE.contains(key)){
            CONFIG_INSTANCE.add(key, def);
            return def;
        }

        return CONFIG_INSTANCE.get(key);
    }

    public static void init() throws IOException {
        Cyanidin.LOGGER.info("Loading config file.");

        if (!CONFIG_FILE.exists()){
            Cyanidin.LOGGER.info("Config file not found! Creating new config file.");
            CONFIG_FILE.createNewFile();
        }

        CONFIG_INSTANCE = CommentedFileConfig.ofConcurrent(CONFIG_FILE);

        CONFIG_INSTANCE.load();

        try {
            loadOrDefaultValues();
        }catch (Exception e){
            Cyanidin.LOGGER.error("Failed to load config file!", e);
        }

        CONFIG_INSTANCE.save();
    }
}
