package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Float32V(Range range, Float value) implements FractionalV { }
