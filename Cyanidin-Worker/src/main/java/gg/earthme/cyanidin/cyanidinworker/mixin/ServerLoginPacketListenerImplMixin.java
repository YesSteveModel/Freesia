package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow @Nullable
    String requestedUsername;
    @Shadow abstract void startClientVerification(GameProfile gameProfile);

    /**
     * @author MrHua269
     * @reason Kill UUID checks
     */
    @Overwrite
    public void handleHello(@NotNull ServerboundHelloPacket serverboundHelloPacket) {
        this.requestedUsername = serverboundHelloPacket.name();
        this.startClientVerification(new GameProfile(serverboundHelloPacket.profileId(), this.requestedUsername));
    }
}
