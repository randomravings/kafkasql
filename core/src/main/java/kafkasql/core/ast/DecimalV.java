package kafkasql.core.ast;

import java.math.BigDecimal;

import kafkasql.core.Range;

public final record DecimalV(Range range, BigDecimal value) implements FractionalV
{
    public byte precision() { return (byte) value.precision(); }
    public byte scale() { return (byte) value.scale(); }
}
