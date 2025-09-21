package kafkasql.core.ast;

import kafkasql.core.Range;

public final record DistributeClause(Range range, IdentifierList keys) implements AstNode { }
