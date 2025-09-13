package streamsql.ast;

public record UseContext(Context context, boolean absolute) implements UseStmt {}