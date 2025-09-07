package streamsql.ast;

public final class UInt32V implements Numeric<UInt32V, Long> {
    private final long value;
    public UInt32V(long value) {
        if (value < 0 || value > 0xFFFFFFFFL)
            throw new IllegalArgumentException("UINT32 out of range: " + value);
        this.value = value;
    }
    public long value() { return value; }
    public UInt32V add(UInt32V other) { return new UInt32V((this.value + other.value) & 0xFFFFFFFFL); }
    public UInt32V subtract(UInt32V other) { return new UInt32V((this.value - other.value) & 0xFFFFFFFFL); }
    public UInt32V multiply(UInt32V other) { return new UInt32V((this.value * other.value) & 0xFFFFFFFFL); }
    public UInt32V divide(UInt32V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new UInt32V((this.value / other.value) & 0xFFFFFFFFL);
    }
}
