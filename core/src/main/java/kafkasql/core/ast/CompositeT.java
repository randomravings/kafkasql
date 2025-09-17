package kafkasql.core.ast;

public sealed interface CompositeT extends AnyT permits ListT, MapT { }