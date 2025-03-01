package meow.kikir.freesia.worker.mixin;

import com.mojang.authlib.GameProfile;
import meow.kikir.freesia.common.EntryPoint;
import meow.kikir.freesia.worker.ServerLoader;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow
    @Nullable
    String requestedUsername;

    @Shadow
    abstract void startClientVerification(GameProfile gameProfile);

    /**
     * @author MrHua269
     * @reason Kill UUID checks and preload player data
     */
    @Overwrite
    public void handleHello(@NotNull ServerboundHelloPacket serverboundHelloPacket) {
        this.requestedUsername = serverboundHelloPacket.name();
        final GameProfile requestedProfile = new GameProfile(serverboundHelloPacket.profileId(), this.requestedUsername);

        //Preload it to prevent load it blocking
        ServerLoader.workerConnection.getPlayerData(requestedProfile.getId(), data -> {
            if (data != null) {
                ServerLoader.playerDataCache.put(requestedProfile.getId(), data);
            }

            EntryPoint.LOGGER_INST.info("Pre-loaded player data for player {}.", requestedProfile.getName());
            this.startClientVerification(requestedProfile); //Continue login process
        });
    }
}
