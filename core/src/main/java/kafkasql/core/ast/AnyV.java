package kafkasql.core.ast;

public sealed interface AnyV extends Expr
    permits NullV, PrimitiveV, CompositeV, ComplexV { }
