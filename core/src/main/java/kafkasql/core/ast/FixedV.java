package kafkasql.core.ast;

public final record FixedV(Range range, byte[] value) implements BinaryV { }
