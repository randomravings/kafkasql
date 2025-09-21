package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Int32V(Range range, Integer value) implements IntegerV { }
