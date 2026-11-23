package meow.kikir.freesia.common.communicating.file;

import meow.kikir.freesia.common.utils.LinkedObjects;

import java.nio.channels.FileChannel;

public record FileChunks(
        int totalChunks,
        FileChannel channel,
        LinkedObjects<FileChunk> chunks
) {
}
