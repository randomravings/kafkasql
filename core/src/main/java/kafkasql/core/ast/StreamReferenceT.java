package kafkasql.core.ast;

import kafkasql.core.Range;

public final record StreamReferenceT(Range range, Identifier alias, TypeReference ref, AstOptionalNode<DistributeClause> distributeClause) implements StreamType {}
