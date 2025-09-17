package kafkasql.core.ast;

import java.math.BigDecimal;

public final record DecimalV(Range range, BigDecimal value) implements FractionalV { }
