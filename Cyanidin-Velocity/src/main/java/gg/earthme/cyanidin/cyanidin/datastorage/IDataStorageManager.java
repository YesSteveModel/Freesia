package gg.earthme.cyanidin.cyanidin.datastorage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IDataStorageManager {
    CompletableFuture<String> loadPlayerData(UUID playerUUID);

    CompletableFuture<Void> save(UUID playerUUID, String content);
}
