package streamsql.ast;

public final record TimestampTzT(Range range, byte precision) implements TemporalT { }
