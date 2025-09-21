package kafkasql.core.ast;

import kafkasql.core.Range;

public final record WhereClause(Range range, Expr expr) implements AstNode {}
