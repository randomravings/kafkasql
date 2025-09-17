package streamsql.ast;

public final record FixedT(Range range, int size) implements BinaryT { }