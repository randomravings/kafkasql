package kafkasql.core.ast;

import kafkasql.core.Range;

public final record ProjectionExpr(Range range, Expr expr, AstOptionalNode<Identifier> alias) implements AstNode { }
