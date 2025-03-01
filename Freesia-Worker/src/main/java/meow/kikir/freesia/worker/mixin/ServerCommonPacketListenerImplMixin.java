package meow.kikir.freesia.worker.mixin;

import net.minecraft.Util;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Shadow
    private long keepAliveTime;

    @Shadow
    private int latency;

    @Shadow
    private boolean keepAlivePending;

    @Inject(method = "handleKeepAlive", at = @At(value = "HEAD"), cancellable = true)
    public void onKeepaliveHandle(ServerboundKeepAlivePacket serverboundKeepAlivePacket, @NotNull CallbackInfo ci) {
        //Do not check keepalive id
        int i = (int) (Util.getMillis() - this.keepAliveTime);
        this.latency = (this.latency * 3 + i) / 4;
        this.keepAlivePending = false;
        ci.cancel();
    }
}
