package meow.kikir.freesia.velocity;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.netty.channel.unix.DomainSocketAddress;
import meow.kikir.freesia.common.utils.DomainUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class FreesiaConfig {
    public static List<SocketAddress> workerMessionAddresses = new ArrayList<>();
    public static InetSocketAddress masterServiceAddress = new InetSocketAddress("127.0.0.1", 19200);
    public static String languageName = "zh_CN";
    public static boolean kickIfYsmNotInstalled = false;
    public static int ysmDetectionTimeout = 3000;

    private static CommentedFileConfig CONFIG_INSTANCE;

    private static void loadOrDefaultValues() {
        final List<SocketAddress> resloved = new ArrayList<>();
        for (String singleEntry : get("workers", List.of("127.0.0.1:19199"))) {
            final String[] split = singleEntry.split(":");

            if (split.length < 2) {
                final boolean isUnixDomain = DomainUtils.isUnixDomainSocketAddress(singleEntry);
                if (isUnixDomain) {
                    DomainUtils.throwIfUDSIsUnavailable();

                    resloved.add(new DomainSocketAddress(singleEntry));
                    continue;
                }

                Freesia.LOGGER.warn("Ignoring invalid worker msession address entry: {}", singleEntry);
                continue;
            }

            try {
                int parsedPort = Integer.parseInt(split[1]);
                resloved.add(new InetSocketAddress(split[0], parsedPort));
            }catch (Exception e) {
                Freesia.LOGGER.warn("Ignoring invalid worker msession address entry: {}", singleEntry, e);
            }
        }

        workerMessionAddresses = resloved;

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
