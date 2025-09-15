package streamsql.ast;

public sealed interface BinaryT extends PrimitiveT
    permits BytesT, FixedT { }
