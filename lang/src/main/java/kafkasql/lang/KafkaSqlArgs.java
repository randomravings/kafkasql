package kafkasql.lang;

import java.nio.file.Path;

public final record KafkaSqlArgs(
    Path workspaceRoot,
    boolean resolveIncludes,
    boolean trace
) { }
