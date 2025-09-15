package streamsql.ast;

public sealed interface AnyV extends Expr
    permits NullV, PrimitiveV, CompositeV, ComplexV { }
