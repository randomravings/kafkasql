package kafkasql.core.ast;

public final record CheckClause(Range range, AstOptionalNode<Identifier> name, Expr expr) implements AstNode { }
