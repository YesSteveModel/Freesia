package meow.kikir.freesia.common.communicating.file;

import java.nio.file.Path;
import java.util.function.Function;

public record FileChunk(
        Path pathRelativity,
        Function<FileChunk, byte[]> reader,
        int offset,
        int length
) {
}
