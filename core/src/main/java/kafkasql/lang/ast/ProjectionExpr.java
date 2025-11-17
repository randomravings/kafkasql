package kafkasql.lang.ast;

public final record ProjectionExpr(Range range, Expr expr, AstOptionalNode<Identifier> alias) implements AstNode { }
