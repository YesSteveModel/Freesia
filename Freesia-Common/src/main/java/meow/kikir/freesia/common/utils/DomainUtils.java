package meow.kikir.freesia.common.utils;

import io.netty.channel.unix.DomainSocketAddress;

public class DomainUtils {
    public static boolean isUnixDomainSocketAddress(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        char firstChar = host.charAt(0);
        return firstChar == '/' || firstChar == '@';
    }

    public static void throwIfUDSIsUnavailable() {
        if (!isUdsSupported()) {
            throw new UnsupportedOperationException("UnixDomainSocket is not supported on your machine!");
        }
    }

    public static boolean isUdsSupported() {
        try {
            new DomainSocketAddress("/dev/null");
        } catch (Error | Exception e) {
            return false;
        }

        return true;
    }
}
