package streamsql.ast;

public sealed interface AnyV extends Expr
    permits Literal, ListV, MapV { }
