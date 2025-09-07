package streamsql.ast;

public final class UInt16V implements Numeric<UInt16V, Integer> {
    private final int value;
    public UInt16V(int value) {
        if (value < 0 || value > 0xFFFF)
            throw new IllegalArgumentException("UINT16 out of range: " + value);
        this.value = value;
    }
    public int value() { return value; }
    public UInt16V add(UInt16V other) { return new UInt16V((this.value + other.value) & 0xFFFF); }
    public UInt16V subtract(UInt16V other) { return new UInt16V((this.value - other.value) & 0xFFFF); }
    public UInt16V multiply(UInt16V other) { return new UInt16V((this.value * other.value) & 0xFFFF); }
    public UInt16V divide(UInt16V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new UInt16V((this.value / other.value) & 0xFFFF);
    }
}
