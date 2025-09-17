package kafkasql.core.ast;

public final record StreamReferenceT(Range range, Identifier alias, TypeReference ref, AstOptionalNode<DistributeClause> distributeClause) implements StreamType {}
