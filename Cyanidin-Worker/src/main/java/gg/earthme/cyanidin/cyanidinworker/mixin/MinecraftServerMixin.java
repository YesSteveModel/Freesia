package gg.earthme.cyanidin.cyanidinworker.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 600)
public class MinecraftServerMixin {
    /**
     * @author MrHua269
     * @reason Kill the ticking
     */
    @Redirect(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    public void tickLevelHook(ServerLevel instance, BooleanSupplier booleanSupplier) {

    }

    @Redirect(method = "saveEverything", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z"))
    public boolean saveAllChunksHook(MinecraftServer instance, boolean bl, boolean bl2, boolean bl3){
        return true;
    }
}
