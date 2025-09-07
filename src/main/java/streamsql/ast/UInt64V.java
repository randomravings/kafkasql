package streamsql.ast;

import java.math.BigInteger;

public final class UInt64V implements Numeric<UInt64V, BigInteger> {
    private final BigInteger value;
    public UInt64V(BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 64)
            throw new IllegalArgumentException("UINT64 out of range: " + value);
        this.value = value;
    }
    public BigInteger value() { return value; }
    public UInt64V add(UInt64V other) {
        BigInteger sum = this.value.add(other.value);
        if (sum.signum() < 0 || sum.bitLength() > 64)
            throw new ArithmeticException("UINT64 overflow");
        return new UInt64V(sum);
    }
    public UInt64V subtract(UInt64V other) {
        BigInteger diff = this.value.subtract(other.value);
        if (diff.signum() < 0 || diff.bitLength() > 64)
            throw new ArithmeticException("UINT64 underflow");
        return new UInt64V(diff);
    }
    public UInt64V multiply(UInt64V other) {
        BigInteger prod = this.value.multiply(other.value);
        if (prod.signum() < 0 || prod.bitLength() > 64)
            throw new ArithmeticException("UINT64 overflow");
        return new UInt64V(prod);
    }
    public UInt64V divide(UInt64V other) {
        if (other.value.equals(BigInteger.ZERO)) throw new ArithmeticException("Divide by zero");
        BigInteger quot = this.value.divide(other.value);
        return new UInt64V(quot);
    }
}
