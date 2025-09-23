package kafkasql.core.ast;

import kafkasql.core.Range;

public final record CheckClause(Range range, AstOptionalNode<Identifier> name, Expr expr) implements AstNode { }
