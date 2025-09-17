package kafkasql.core.ast;

public sealed interface BinaryT extends PrimitiveT
    permits BytesT, FixedT { }
