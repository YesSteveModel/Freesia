package gg.earthme.cyanidin.cyanidinworker.mixin;

import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "saveWithoutId", at = @At(value = "RETURN"))
    public void onEntityDataSave(CompoundTag entityDataNbt, CallbackInfoReturnable<CompoundTag> cir){
        final Entity thisEntity = (Entity) (Object) this;

        if (thisEntity instanceof Player player){
            final CompoundTag ysmData = entityDataNbt.getCompound("ysm");

            ServerLoader.playerDataCache.asMap().replace(player.getUUID(), ysmData);
            ServerLoader.workerConnection.updatePlayerData(player.getUUID(), ysmData);
        }
    }

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", shift = At.Shift.BEFORE))
    public void onEntityDataLoad(CompoundTag entityDataNbt, CallbackInfo ci){
        final Entity thisEntity = (Entity) (Object) this;

        CompoundTag ysmData = null;
        if (thisEntity instanceof Player player){
            final CompoundTag hit = ServerLoader.playerDataCache.getIfPresent(player.getUUID());

            if (hit != null){
                ysmData = hit;
            }else{
                CompletableFuture<CompoundTag> callback = new CompletableFuture<>();
                ServerLoader.workerConnection.getPlayerData(player.getUUID(), callback::complete);
                CompoundTag got = callback.join();

                if (got != null){
                    ServerLoader.playerDataCache.put(player.getUUID(), got);
                    ysmData = got;
                }
            }
        }

        if (ysmData != null){
            entityDataNbt.remove("ysm");
            entityDataNbt.put("ysm", ysmData);
        }
    }
}
