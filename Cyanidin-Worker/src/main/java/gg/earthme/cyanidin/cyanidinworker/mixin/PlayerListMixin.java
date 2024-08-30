package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "canPlayerLogin", at = @At(value = "HEAD"), cancellable = true)
    public void playerList$canJoin$kill(SocketAddress socketAddress, GameProfile gameProfile, @NotNull CallbackInfoReturnable<Component> cir){
        cir.setReturnValue(null);
    }
}
