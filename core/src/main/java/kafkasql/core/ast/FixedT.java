package kafkasql.core.ast;

import kafkasql.core.Range;

public final record FixedT(Range range, int size) implements BinaryT { }