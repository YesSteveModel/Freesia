package gg.earthme.cyanidin.cyanidinworker.mixin;

import gg.earthme.cyanidin.cyanidinworker.ServerLoader;
import i.mrhua269.cyanidin.common.EntryPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
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

        if (entityDataNbt.contains("cyanidin_null_entity")){
            return;
        }

        if (thisEntity instanceof Player player){
            final CompoundTag ysmData = entityDataNbt.getCompound("ysm");

            ServerLoader.playerDataCache.asMap().replace(player.getUUID(), ysmData);
            ServerLoader.workerConnection.updatePlayerData(player.getUUID(), ysmData);
        }
    }

    @Inject(method = "load", at = @At(value = "HEAD"))
    public void onEntityDataLoad(@NotNull CompoundTag entityDataNbt, CallbackInfo ci){
        final Entity thisEntity = (Entity) (Object) this;

        if (entityDataNbt.contains("cyanidin_do_not_pull_from_master")){
            entityDataNbt.remove("cyanidin_do_not_pull_from_master"); //As we just want to sync it once instead of loading it
            if (thisEntity instanceof Player player){
                ServerLoader.playerDataCache.asMap().replace(player.getUUID(), entityDataNbt.getCompound("ysm"));
                EntryPoint.LOGGER_INST.info("Synced entity ysm data from master controller service point for player {}", player.getScoreboardName());
            }
            return;
        }

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
                }else{
                    EntryPoint.LOGGER_INST.info("Generating default ysm data for entity id {} uuid: {}", thisEntity.getId(), thisEntity.getUUID());
                }
            }
        }

        if (ysmData != null){
            entityDataNbt.remove("ysm");
            entityDataNbt.put("ysm", ysmData);
        }
    }
}
