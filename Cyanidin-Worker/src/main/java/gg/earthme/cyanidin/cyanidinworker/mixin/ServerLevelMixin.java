package gg.earthme.cyanidin.cyanidinworker.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    /**
     * @author MrHua269
     * @reason Kill the ticking
     */
    @Overwrite
    public void tick(BooleanSupplier booleanSupplier) {

    }
}
