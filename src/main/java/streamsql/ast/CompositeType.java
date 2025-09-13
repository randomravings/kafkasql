package streamsql.ast;

public sealed interface CompositeType extends AnyT permits ListT, MapT { }