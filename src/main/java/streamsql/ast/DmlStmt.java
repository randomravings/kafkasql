package streamsql.ast;

public sealed interface DmlStmt extends Stmt permits ReadStmt, WriteStmt {}
