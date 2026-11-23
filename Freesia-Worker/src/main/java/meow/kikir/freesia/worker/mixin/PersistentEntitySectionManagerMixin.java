package meow.kikir.freesia.worker.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin<T extends EntityAccess> {
    @Shadow
    @Final
    LevelCallback<T> callbacks;

    /**
     * @author MrHua269
     * @reason Kill entity id duplicate detection
     */
    @Overwrite
    void startTracking(T entity) {
        this.callbacks.onTrackingStart(entity);
    }

    /**
     * @author MrHua269
     * @reason Kill entity id duplicate detection
     */
    @Overwrite
    void stopTracking(T entity) {
        this.callbacks.onTrackingEnd(entity);
    }
}
