package kafkasql.core.ast;

import kafkasql.core.Range;

public final record BytesT(Range range) implements BinaryT { }
