package streamsql.ast;

public final class UInt8V implements Numeric<UInt8V, Short> {
    private final short value;
    public UInt8V(short value) {
        if (value < 0 || value > 0xFF)
            throw new IllegalArgumentException("UINT8 out of range: " + value);
        this.value = value;
    }
    public short value() { return value; }
    public UInt8V add(UInt8V other) { return new UInt8V((short)((this.value + other.value) & 0xFF)); }
    public UInt8V subtract(UInt8V other) { return new UInt8V((short)((this.value - other.value) & 0xFF)); }
    public UInt8V multiply(UInt8V other) { return new UInt8V((short)((this.value * other.value) & 0xFF)); }
    public UInt8V divide(UInt8V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new UInt8V((short)((this.value / other.value) & 0xFF));
    }
}
