package streamsql.ast;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalV implements Numeric<DecimalV, BigDecimal> {
    private final BigDecimal value;
    private final int precision;
    private final int scale;
    public DecimalV(BigDecimal value, int precision, int scale) {
        if (value.precision() > precision)
            throw new IllegalArgumentException("Decimal precision exceeded: " + value.precision() + " > " + precision);
        if (value.scale() > scale)
            throw new IllegalArgumentException("Decimal scale exceeded: " + value.scale() + " > " + scale);
        this.value = value.setScale(scale);
        this.precision = precision;
        this.scale = scale;
    }
    public BigDecimal value() { return value; }
    public int precision() { return precision; }
    public int scale() { return scale; }
    @Override
    public DecimalV add(DecimalV other) {
        return new DecimalV(this.value.add(other.value).setScale(scale, RoundingMode.HALF_UP), this.precision, this.scale);
    }
    @Override
    public DecimalV subtract(DecimalV other) {
        return new DecimalV(this.value.subtract(other.value).setScale(scale, RoundingMode.HALF_UP), this.precision, this.scale);
    }
    @Override
    public DecimalV multiply(DecimalV other) {
        return new DecimalV(this.value.multiply(other.value).setScale(scale, RoundingMode.HALF_UP), this.precision, this.scale);
    }
    @Override
    public DecimalV divide(DecimalV other) {
        return new DecimalV(this.value.divide(other.value).setScale(scale, RoundingMode.HALF_UP), this.precision, this.scale);
    }
}
