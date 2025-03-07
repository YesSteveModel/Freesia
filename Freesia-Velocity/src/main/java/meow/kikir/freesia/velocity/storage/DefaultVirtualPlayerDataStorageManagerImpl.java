package meow.kikir.freesia.velocity.storage;

import meow.kikir.freesia.velocity.FreesiaConstants;
import meow.kikir.freesia.velocity.Freesia;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DefaultVirtualPlayerDataStorageManagerImpl implements IDataStorageManager {
    @Override
    public CompletableFuture<byte[]> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            final File targetFile = new File(FreesiaConstants.FileConstants.VIRTUAL_PLAYER_DATA_DIR, playerUUID + ".dat");

            if (!targetFile.exists()) {
                return null;
            }

            try {
                return Files.readAllBytes(targetFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioTask -> Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, ioTask).schedule());
    }


    @Override
    public CompletableFuture<Void> save(UUID playerUUID, byte[] content) {
        return CompletableFuture.runAsync(() -> {
            final File targetFile = new File(FreesiaConstants.FileConstants.VIRTUAL_PLAYER_DATA_DIR, playerUUID + ".dat");

            try {
                Files.write(targetFile.toPath(), content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioTask -> Freesia.PROXY_SERVER.getScheduler().buildTask(Freesia.INSTANCE, ioTask).schedule());
    }
}
