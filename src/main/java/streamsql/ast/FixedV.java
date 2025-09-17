package streamsql.ast;

public final record FixedV(Range range, byte[] value) implements BinaryV { }
