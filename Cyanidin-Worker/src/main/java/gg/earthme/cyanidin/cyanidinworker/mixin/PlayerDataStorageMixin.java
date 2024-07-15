package gg.earthme.cyanidin.cyanidinworker.mixin;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {

    @Shadow @Final private File playerDir;

    @Shadow protected abstract Optional<CompoundTag> load(Player player, String string);

    @Shadow @Final protected DataFixer fixerUpper;

    @Inject(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", shift = At.Shift.BEFORE), cancellable = true)
    public void onPlayerSaveCalled(Player player, @NotNull CallbackInfo ci){
        ci.cancel();
        try {
            CompoundTag compoundTag = player.saveWithoutId(new CompoundTag());
            System.out.println(compoundTag.get("ysm"));
            Path path = this.playerDir.toPath();
            Path path2 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");
            NbtIo.writeCompressed(compoundTag, path2);
            Path path3 = path.resolve(player.getStringUUID() + ".dat");
            Path path4 = path.resolve(player.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(path3, path2, path4);
        } catch (Exception var7) {
            LogUtils.getLogger().warn("Failed to save player data for {}", player.getName().getString());
        }
    }

    @Inject(method = "load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;", at = @At(value = "HEAD"), cancellable = true)
    public void onLoadCalled(Player player, @NotNull CallbackInfoReturnable<Optional<CompoundTag>> cir){
        Optional<CompoundTag> optional = this.load(player, ".dat");

        cir.setReturnValue(optional.or(() -> this.load(player, ".dat_old")).map((compoundTag) -> {
            System.out.println(compoundTag.get("ysm"));

            int i = NbtUtils.getDataVersion(compoundTag, -1);
            compoundTag = DataFixTypes.PLAYER.updateToCurrentVersion(this.fixerUpper, compoundTag, i);
            player.load(compoundTag);
            return compoundTag;
        }));
    }
}
