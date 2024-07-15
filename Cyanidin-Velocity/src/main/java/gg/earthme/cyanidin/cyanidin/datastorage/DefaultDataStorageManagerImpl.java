package gg.earthme.cyanidin.cyanidin.datastorage;

import gg.earthme.cyanidin.cyanidin.Cyanidin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DefaultDataStorageManagerImpl implements IDataStorageManager{
    private static final File PLUGIN_FOLDER = new File(new File("plugins"), "Cyanidin");
    private static final File PLAYER_DATA_FOLDER = new File(PLUGIN_FOLDER, "playerdata");

    static {
        PLAYER_DATA_FOLDER.mkdirs();
    }

    @Override
    public CompletableFuture<String> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            final File targetFile = new File(PLAYER_DATA_FOLDER, playerUUID + "_base64ed.txt");

            if (!targetFile.exists()){
                System.out.println("IGNORED");
                return null;
            }

            try {
                final String read = Files.readString(targetFile.toPath());
                return read.isBlank() ? null : read;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioTask -> Cyanidin.PROXY_SERVER.getScheduler().buildTask(Cyanidin.INSTANCE, ioTask).schedule());
    }

    @Override
    public CompletableFuture<Void> save(UUID playerUUID, String content) {
        return CompletableFuture.runAsync(() -> {
            final File targetFile = new File(PLAYER_DATA_FOLDER, playerUUID + "_base64ed.txt");

            try {
                if (!targetFile.exists()){
                    targetFile.createNewFile();
                }

                Files.writeString(targetFile.toPath(), content);
            }catch (IOException e){
                throw new RuntimeException(e);
            }
        }, ioTask -> Cyanidin.PROXY_SERVER.getScheduler().buildTask(Cyanidin.INSTANCE, ioTask).schedule());
    }
}
