package kafkasql.lang.ast;

public sealed interface BinaryT extends PrimitiveT
    permits BytesT, FixedT { }
