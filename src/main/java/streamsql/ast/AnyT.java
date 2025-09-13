package streamsql.ast;

public sealed interface AnyT
    permits VoidT, PrimitiveType, CompositeType, ComplexType, TypeRef { }
