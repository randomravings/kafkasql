package kafkasql.core.ast;

import kafkasql.core.Range;

public final record StreamInlineT(Range range, Identifier alias, FieldList fields, AstOptionalNode<DistributeClause> distributeClause) implements StreamType { }
