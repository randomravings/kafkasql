package kafkasql.core.ast;

import kafkasql.core.Range;

public final record DecimalT(Range range, byte precision, byte scale) implements FractionalT { }