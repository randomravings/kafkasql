package streamsql.ast;

public final record DistributeClause(Range range, IdentifierList keys) implements AstNode { }
