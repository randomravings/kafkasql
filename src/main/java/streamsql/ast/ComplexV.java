package streamsql.ast;

public sealed interface ComplexV extends AnyV
    permits StructV, EnumV, UnionV, ScalarV { }
