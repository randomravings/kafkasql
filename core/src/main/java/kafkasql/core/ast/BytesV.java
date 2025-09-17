package kafkasql.core.ast;

public final record BytesV(Range range, byte[] value) implements BinaryV { }
