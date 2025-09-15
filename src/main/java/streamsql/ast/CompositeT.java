package streamsql.ast;

public sealed interface CompositeT extends AnyT permits ListT, MapT { }