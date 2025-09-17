package kafkasql.core.ast;

public sealed interface BinaryV extends PrimitiveV
    permits BytesV, FixedV { }
