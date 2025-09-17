package kafkasql.core.ast;

public sealed interface Projection extends AstNode
    permits ProjectionAll, ProjectionList { }
