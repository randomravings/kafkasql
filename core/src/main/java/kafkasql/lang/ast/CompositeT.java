package kafkasql.lang.ast;

public sealed interface CompositeT extends AnyT permits ListT, MapT { }