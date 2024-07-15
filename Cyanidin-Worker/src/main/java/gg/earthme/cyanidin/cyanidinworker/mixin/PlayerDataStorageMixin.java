package gg.earthme.cyanidin.cyanidinworker.mixin;

import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", shift = At.Shift.BEFORE), cancellable = true)
    public void onPlayerSaveCalled(@NotNull Player player, @NotNull CallbackInfo ci){
        ci.cancel();

        CompoundTag compoundTag = player.saveWithoutId(new CompoundTag());
        ServerLoader.workerConnection.updatePlayerData(player.getUUID(), compoundTag);
    }

    @Inject(method = "load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;", at = @At(value = "HEAD"), cancellable = true)
    public void onLoadCalled(@NotNull Player player, @NotNull CallbackInfoReturnable<Optional<CompoundTag>> cir){
        CompletableFuture<CompoundTag> callback = new CompletableFuture<>();
        ServerLoader.workerConnection.getPlayerData(player.getUUID(), callback::complete);
        final CompoundTag got = callback.join();

        if (got == null){
            cir.setReturnValue(Optional.empty());
            return;
        }

        cir.setReturnValue(Optional.of(got));
    }
}
