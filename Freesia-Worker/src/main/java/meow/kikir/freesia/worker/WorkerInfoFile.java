package meow.kikir.freesia.worker;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public record WorkerInfoFile(UUID workerUUID, String workerName) {

    public static @NotNull WorkerInfoFile readOrCreate(@NotNull File targetFile) throws IOException {
        if (!targetFile.exists()) {
            final String workerName = "freesia_node_" + System.currentTimeMillis();
            WorkerInfoFile created = new WorkerInfoFile(UUID.nameUUIDFromBytes(workerName.getBytes(StandardCharsets.UTF_8)), workerName);

            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(bos);

            dos.writeUTF(created.workerName());
            dos.writeLong(created.workerUUID().getMostSignificantBits());
            dos.writeLong(created.workerUUID().getLeastSignificantBits());
            dos.flush();

            Files.write(targetFile.toPath(), bos.toByteArray());
            return created;
        }

        final ByteArrayInputStream bis = new ByteArrayInputStream(Files.readAllBytes(targetFile.toPath()));
        final DataInputStream dis = new DataInputStream(bis);

        final String workerName = dis.readUTF();
        final UUID workerUUID = new UUID(dis.readLong(), dis.readLong());
        return new WorkerInfoFile(workerUUID, workerName);
    }
}
