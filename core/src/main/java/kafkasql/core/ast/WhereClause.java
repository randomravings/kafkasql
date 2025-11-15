package kafkasql.core.ast;

public final record WhereClause(Range range, Expr expr) implements AstNode {}
