package streamsql.ast;

public sealed interface AnyT
    permits VoidT, PrimitiveT, CompositeT, ComplexT, TypeRef { }
