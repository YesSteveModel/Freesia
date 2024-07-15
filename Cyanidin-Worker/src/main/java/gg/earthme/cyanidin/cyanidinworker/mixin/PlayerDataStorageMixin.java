package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.mojang.datafixers.DataFixer;
import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Shadow @Final protected DataFixer fixerUpper;

    @Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", shift = At.Shift.BEFORE), cancellable = true)
    public void onPlayerSaveCalled(@NotNull Player player, @NotNull CallbackInfo ci){
        ci.cancel();

        CompoundTag compoundTag = player.saveWithoutId(new CompoundTag());
        ServerLoader.workerConnection.updatePlayerData(player.getUUID(), compoundTag);
    }

    /**
     * @author MrHua269
     * @reason Use our data storages
     */
    @Overwrite
    public Optional<CompoundTag> load(@NotNull Player player){
        CompletableFuture<CompoundTag> callback = new CompletableFuture<>();
        ServerLoader.workerConnection.getPlayerData(player.getUUID(), callback::complete);
        CompoundTag got = callback.join();

        if (got == null){
            return Optional.empty();
        }

        int i = NbtUtils.getDataVersion(got, -1);
        got = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, got, i);

        player.load(got);
        return Optional.of(got);
    }
}
