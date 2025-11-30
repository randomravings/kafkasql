package kafkasql.runtime.type;

public final record MapType(
    PrimitiveType key,
    AnyType value
) implements CompositeType { }
