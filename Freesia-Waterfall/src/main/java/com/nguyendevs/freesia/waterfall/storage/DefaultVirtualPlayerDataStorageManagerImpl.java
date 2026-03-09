package com.nguyendevs.freesia.waterfall.storage;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTLimiter;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import com.nguyendevs.freesia.waterfall.FreesiaConstants;
import com.nguyendevs.freesia.waterfall.Freesia;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DefaultVirtualPlayerDataStorageManagerImpl implements IDataStorageManager {
    @Override
    public CompletableFuture<byte[]> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            final File targetFile = new File(FreesiaConstants.FileConstants.VIRTUAL_PLAYER_DATA_DIR,
                    playerUUID + ".dat");

            if (!targetFile.exists()) {
                return null;
            }

            try {
                // Covert to unnamed nbt
                final byte[] data = Files.readAllBytes(targetFile.toPath());
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                final DataInputStream dataInputStream = new DataInputStream(inputStream);

                final DefaultNBTSerializer codec = new DefaultNBTSerializer();

                final NBTCompound deserialized = (NBTCompound) codec.deserializeTag(NBTLimiter.noop(), dataInputStream,
                        true);

                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                codec.serializeTag(dataOutputStream, deserialized, false);

                return outputStream.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioTask -> Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, ioTask));
    }

    @Override
    public CompletableFuture<Void> save(UUID playerUUID, byte[] content) {
        return CompletableFuture.runAsync(() -> {
            final File targetFile = new File(FreesiaConstants.FileConstants.VIRTUAL_PLAYER_DATA_DIR,
                    playerUUID + ".dat");

            try {
                // Covert to named nbt
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final DataInputStream dataInputStream = new DataInputStream(inputStream);

                final DefaultNBTSerializer codec = new DefaultNBTSerializer();

                final NBTCompound deserialized = (NBTCompound) codec.deserializeTag(NBTLimiter.noop(), dataInputStream,
                        false);

                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                codec.serializeTag(dataOutputStream, deserialized, true);

                Files.write(targetFile.toPath(), outputStream.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ioTask -> Freesia.PROXY_SERVER.getScheduler().runAsync(Freesia.INSTANCE, ioTask));
    }
}

