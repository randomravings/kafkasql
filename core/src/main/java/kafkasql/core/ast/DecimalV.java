package kafkasql.core.ast;

import java.math.BigDecimal;

public final record DecimalV(Range range, BigDecimal value) implements FractionalV, Comparable<DecimalV> {
    public byte precision() { return (byte) value.precision(); }
    public byte scale() { return (byte) value.scale(); }
    @Override
    public int compareTo(DecimalV o) {
        return this.value.compareTo(o.value);
    }
}
