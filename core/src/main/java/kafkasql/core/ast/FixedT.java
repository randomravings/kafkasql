package kafkasql.core.ast;

public final record FixedT(Range range, int size) implements BinaryT { }