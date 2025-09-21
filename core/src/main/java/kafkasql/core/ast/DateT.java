package kafkasql.core.ast;

import kafkasql.core.Range;

public final record DateT(Range range) implements TemporalT { }
