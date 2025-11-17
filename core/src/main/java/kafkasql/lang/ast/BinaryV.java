package kafkasql.lang.ast;

public sealed interface BinaryV extends PrimitiveV
    permits BytesV, FixedV { }
