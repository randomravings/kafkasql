package streamsql.ast;

public sealed interface Projection extends AstNode
    permits ProjectionAll, ProjectionList { }
