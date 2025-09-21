package kafkasql.core.ast;

import kafkasql.core.Range;

public final record TimeT(Range range, byte precision) implements TemporalT { }
