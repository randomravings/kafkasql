package kafkasql.core.ast;

public final record TimeT(Range range, byte precision) implements TemporalT { }
