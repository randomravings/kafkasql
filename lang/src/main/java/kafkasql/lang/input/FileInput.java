package kafkasql.lang.input;

import java.nio.file.Path;

public final record FileInput(
    String source,
    Path path
) implements Input {
    public static FileInput of(
        Path path
    ) {
        return new FileInput(path.toString(), path);
    }
}
