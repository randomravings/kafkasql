package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Int8V(Range range, Byte value) implements IntegerV { }
