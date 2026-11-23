package meow.kikir.freesia.worker.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    /**
     * @author MrHua269
     * @reason Force packet sending of ysm
     */
    @Overwrite
    public boolean isAlive() {
        return true;
    }
}
