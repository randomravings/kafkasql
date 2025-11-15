package kafkasql.core.ast;

public final record StreamInlineT(Range range, Identifier alias, FieldList fields, AstOptionalNode<DistributeClause> distributeClause) implements StreamType { }
