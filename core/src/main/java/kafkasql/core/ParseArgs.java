package kafkasql.core;

import java.nio.file.Path;

public final record ParseArgs(Path workspaceRoot, boolean resolveIncludes, boolean trace) { }
