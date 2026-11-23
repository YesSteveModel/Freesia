package meow.kikir.freesia.worker.mixin;

import io.netty.channel.unix.DomainSocketAddress;
import meow.kikir.freesia.common.utils.DomainUtils;
import meow.kikir.freesia.worker.FreesiaWorkerConfig;
import meow.kikir.freesia.worker.utils.ServerConnectionListenerHijack;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {
    @Shadow
    public abstract String getServerIp();

    @Shadow
    @Final
    private static Logger LOGGER;

    @Redirect(method = "initServer", at = @At(value = "INVOKE", target = "Ljava/lang/String;isEmpty()Z"))
    public boolean killIPDetect(String instance) {
        return DomainUtils.isUnixDomainSocketAddress(instance) || instance.isEmpty();
    }

    @Redirect(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    public void overrideTcpServerStart(ServerConnectionListener instance, InetAddress address, int port) throws IOException {
        if (!FreesiaWorkerConfig.useUnixDomainSocket) {
            instance.startTcpServerListener(address, port);
            return;
        }

        String localIp = this.getServerIp();

        if (localIp.isEmpty()) {
            localIp = "/tmp/freesia-worker-" + ProcessHandle.current().pid() + ".sock";
        }

        LOGGER.info("Unix domain socket binding on {}", localIp);

        final Path localIpPath = Path.of(localIp);

        try {
            Files.deleteIfExists(localIpPath);
        } catch (IOException ignored) {}

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(localIpPath);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));

        final ServerConnectionListenerHijack hijacked = (ServerConnectionListenerHijack) instance;

        hijacked.freesia$setUnixDomainSocketAddress(new DomainSocketAddress(localIp));
        hijacked.freesia$startOnUDS();
    }
}
