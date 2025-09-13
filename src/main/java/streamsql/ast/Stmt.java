package streamsql.ast;

public sealed interface Stmt permits UseStmt, CreateStmt, ReadStmt, WriteStmt {}
