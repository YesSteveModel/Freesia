package meow.kikir.freesia.worker.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    private int tickCount;

    /**
     * @author MrHua269
     * @reason Only keep basic logics
     */
    @Overwrite
    public void tick() {
        ++this.tickCount;
        ((ServerCommonPacketListenerImpl) (Object) this).keepConnectionAlive();
    }
}
