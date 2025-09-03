package streamsql.ast;

public sealed interface DataType permits PrimitiveType, CompositeType, ComplexType, TypeRef {}
