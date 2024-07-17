package gg.earthme.cyanidin.cyanidin.network.backend;

import com.velocitypowered.api.proxy.Player;
import gg.earthme.cyanidin.cyanidin.Cyanidin;
import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;
import net.kyori.adventure.audience.Audience;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MasterServerMessageHandler extends NettyServerChannelHandlerLayer {
    @Override
    public CompletableFuture<byte[]> readPlayerData(UUID playerUUID) {
        return Cyanidin.dataStorageManager.loadPlayerData(playerUUID);
    }

    @Override
    public CompletableFuture<Void> savePlayerData(UUID playerUUID, byte[] content) {
        return Cyanidin.dataStorageManager.save(playerUUID, content);
    }

    @Override
    public void onModelReloadResult(UUID requester, boolean succeed) {
        Audience target = null;

        if (requester == null){
           target = Cyanidin.PROXY_SERVER;
        }else{
            final Optional<Player> targetOptional = Cyanidin.PROXY_SERVER.getPlayer(requester);

            if (targetOptional.isPresent()){
                target = targetOptional.get();
            }
        }

        if (target != null){
            if (succeed){
                target.sendMessage(Cyanidin.languageManager.i18n("cyanidin.model_reload_succeed", List.of(), List.of()));
            }else{
                target.sendMessage(Cyanidin.languageManager.i18n("cyanidin.model_reload_failed", List.of(), List.of()));
            }
        }
    }
}
