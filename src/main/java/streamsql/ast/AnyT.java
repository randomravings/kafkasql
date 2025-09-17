package streamsql.ast;

public sealed interface AnyT extends AstNode
    permits VoidT, PrimitiveT, CompositeT, ComplexT, TypeReference { }
