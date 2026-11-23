package meow.kikir.freesia.worker;

import java.nio.file.Path;

public class Constants {
    public static final Path MODELS_PATH_BASE = Path.of("config", "yes_steve_model");
    public static final Path[] MODELS_PATH_ALL = new Path[] {
            MODELS_PATH_BASE.resolve("auth"),
            MODELS_PATH_BASE.resolve("custom")
    };
}
