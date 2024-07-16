package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.mojang.datafixers.DataFixer;
import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@Mixin(value = MinecraftServer.class, priority = 600)
public abstract class MinecraftServerMixin {
    @Shadow public abstract ServerConnectionListener getConnection();

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

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void staticBlockInject(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci){
        ServerLoader.SERVER_INST = (MinecraftServer)((Object) this);
    }

    @Inject(method = "tickChildren", at = @At(value = "HEAD"), cancellable = true)
    public void onTickChildrenCall(BooleanSupplier booleanSupplier, @NotNull CallbackInfo ci){
        ci.cancel();
        this.getConnection().tick();
    }
}
