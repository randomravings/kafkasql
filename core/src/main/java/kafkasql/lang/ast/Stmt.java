package kafkasql.lang.ast;

public sealed interface Stmt extends AstNode permits UseStmt, CreateStmt, ReadStmt, WriteStmt {}
