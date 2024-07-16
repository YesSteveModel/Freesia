package gg.earthme.cyanidin.cyanidin.datastorage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IDataStorageManager {
    CompletableFuture<byte[]> loadPlayerData(UUID playerUUID);

    CompletableFuture<Void> save(UUID playerUUID, byte[] content);
}
