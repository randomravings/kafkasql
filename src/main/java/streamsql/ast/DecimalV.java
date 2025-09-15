package streamsql.ast;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Decimal value with precision up to 38
@SuppressWarnings("unchecked")
public final class DecimalV implements FractionalV {
    public AnyT type() { return DecimalT.get(precision(), scale()); }
    public static final DecimalV ZERO = new DecimalV(BigDecimal.ZERO);
    public static final byte MAX_PRECISION = 38;
    private final BigDecimal value;

    public DecimalV(BigDecimal value) {
        if (value.precision() > 38)
            throw new IllegalArgumentException("Decimal precision cannot exceed 38");
        this.value = value;
    }

    public BigDecimal value() { return value; }
    public Int8V precision() { return new Int8V((byte) value.precision()); }
    public Int8V scale() { return new Int8V((byte) value.scale()); }
    public DecimalV add(DecimalV other) {
        return new DecimalV(this.value.add(other.value));
    }
    public DecimalV subtract(DecimalV other) {
        return new DecimalV(this.value.subtract(other.value));
    }
    public DecimalV multiply(DecimalV other) {
        return new DecimalV(this.value.multiply(other.value));
    }
    public DecimalV divide(DecimalV other) {
        return new DecimalV(this.value.divide(other.value));
    }
    public void setScale(int scale, RoundingMode roundingMode) {
        value.setScale(scale, roundingMode);
    }
    public void setScale(int scale) {
        value.setScale(scale, RoundingMode.HALF_UP);
    }
}
