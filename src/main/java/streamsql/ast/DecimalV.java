package streamsql.ast;

import java.math.BigDecimal;
import java.math.RoundingMode;

@SuppressWarnings("unchecked")
public final record DecimalV(BigDecimal value) implements FractionalV {
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
