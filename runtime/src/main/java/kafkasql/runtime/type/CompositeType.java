package kafkasql.runtime.type;

public sealed interface CompositeType extends AnyType
    permits ListType, MapType { }