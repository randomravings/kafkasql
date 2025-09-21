package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Int16V(Range range, Short value) implements IntegerV { }
