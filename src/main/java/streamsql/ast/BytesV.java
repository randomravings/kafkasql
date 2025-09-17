package streamsql.ast;

public final record BytesV(Range range, byte[] value) implements BinaryV { }
