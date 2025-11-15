package kafkasql.core.ast;

public final record TimestampTzT(Range range, byte precision) implements TemporalT { }
