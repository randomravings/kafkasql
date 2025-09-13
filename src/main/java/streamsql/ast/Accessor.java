package streamsql.ast;

public sealed interface Accessor extends Expr
    permits Identifier, Indexer, Segment { }
