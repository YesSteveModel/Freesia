package meow.kikir.freesia.worker.mixin;

import meow.kikir.freesia.worker.ServerLoader;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ServerConfigurationPacketListenerImplMixin {
    @Redirect(method = "handleConfigurationFinished", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V"))
    public void handlePlayerPlacement(PlayerList instance, Connection connection, @NotNull ServerPlayer player, CommonListenerCookie cookie) {
        final Integer entityId = ServerLoader.playerEntityIdMap.get(player.getUUID());

        if (entityId == null) {
            throw new IllegalStateException("No present fetched entity Id for player " + player.getScoreboardName() + " (" + player.getUUID() + ") !");
        }

        player.setId(entityId);
        instance.placeNewPlayer(connection, player, cookie);
    }
}
