package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Int64V(Range range, Long value) implements IntegerV { }
