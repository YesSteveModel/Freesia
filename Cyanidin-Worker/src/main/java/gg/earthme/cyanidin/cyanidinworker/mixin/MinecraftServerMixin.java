package gg.earthme.cyanidin.cyanidinworker.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BooleanSupplier;

@Mixin(value = MinecraftServer.class, priority = 600)
public class MinecraftServerMixin {
    /**
     * @author MrHua269
     * @reason Kill the ticking
     */
    @Redirect(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    public void tickLevel(ServerLevel instance, BooleanSupplier booleanSupplier) {

    }
}
