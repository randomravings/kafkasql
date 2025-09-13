package streamsql.ast;

public sealed interface BinaryT extends PrimitiveType
    permits BytesT, FixedT { }
