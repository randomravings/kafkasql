package streamsql.ast;

public final record StreamInlineT(Range range, Identifier alias, AstListNode<Field> fields, AstOptionalNode<DistributeClause> distributeClause) implements StreamType { }
