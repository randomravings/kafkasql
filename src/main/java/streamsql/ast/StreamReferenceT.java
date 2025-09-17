package streamsql.ast;

public final record StreamReferenceT(Range range, Identifier alias, TypeReference ref, AstOptionalNode<DistributeClause> distributeClause) implements StreamType {}
