package gg.earthme.cyanidin.cyanidin.network.backend;

import gg.earthme.cyanidin.cyanidin.Cyanidin;
import i.mrhua269.cyanidin.common.communicating.handler.NettyServerChannelHandlerLayer;

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
}
