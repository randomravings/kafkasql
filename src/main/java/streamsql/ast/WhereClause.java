package streamsql.ast;

public final record WhereClause(Range range, Expr expr) implements AstNode {}
