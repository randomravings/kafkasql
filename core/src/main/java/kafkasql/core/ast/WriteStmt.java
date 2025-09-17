package kafkasql.core.ast;

public final record WriteStmt(Range range, QName stream, Identifier alias, ListV values) implements Stmt { }