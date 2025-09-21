package kafkasql.core.ast;

import java.math.BigDecimal;

import kafkasql.core.Range;

public final record DecimalV(Range range, BigDecimal value) implements FractionalV { }
