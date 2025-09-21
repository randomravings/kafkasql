package kafkasql.core.ast;

import kafkasql.core.Range;

public final record BoolV(Range range, Boolean value) implements PrimitiveV { }
