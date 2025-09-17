package streamsql.ast;

public final record TimestampT(Range range, byte precision) implements TemporalT { }
