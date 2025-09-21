package kafkasql.core.ast;

import kafkasql.core.Range;

public final record TimestampTzT(Range range, byte precision) implements TemporalT { }
