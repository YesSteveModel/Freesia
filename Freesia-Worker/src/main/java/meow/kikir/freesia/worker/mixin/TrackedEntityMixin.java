package meow.kikir.freesia.worker.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.TrackedEntity.class)
public class TrackedEntityMixin {
    @Inject(method = "updatePlayer", at = @At(value = "HEAD"), cancellable = true)
    public void onUpdatePlayerCall(ServerPlayer serverPlayer, @NotNull CallbackInfo ci){
        ci.cancel(); //Do not send entity status to others just send it to mapper self
    }
}
