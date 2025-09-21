package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Float64V(Range range, Double value) implements FractionalV { }
