package meow.kikir.freesia.worker.utils;

import io.netty.channel.unix.DomainSocketAddress;

import java.net.UnixDomainSocketAddress;

public interface ServerConnectionListenerHijack {
    void freesia$setUnixDomainSocketAddress(DomainSocketAddress udsAddress);

    void freesia$startOnUDS();
}
