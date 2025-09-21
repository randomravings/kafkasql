package kafkasql.core.ast;

import kafkasql.core.Range;

public final record ScalarV(Range range, PrimitiveV value) implements ComplexV { }
