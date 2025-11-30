package kafkasql.runtime.type;

public sealed interface AnyType
    permits PrimitiveType, CompositeType, ComplexType,
            TypeReference, VoidType { }
