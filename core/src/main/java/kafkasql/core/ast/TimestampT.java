package kafkasql.core.ast;

import kafkasql.core.Range;

public final record TimestampT(Range range, byte precision) implements TemporalT { }
