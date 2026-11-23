package meow.kikir.freesia.common.communicating.file;

import java.nio.channels.FileChannel;
import java.nio.file.Path;

public record FileDispatchDesc(
        Path path,
        FileChannel channel
) {
}
