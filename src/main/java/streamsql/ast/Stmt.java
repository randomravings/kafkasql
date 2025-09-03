package streamsql.ast;

public sealed interface Stmt permits UseStmt, DmlStmt, DdlStmt  {}
