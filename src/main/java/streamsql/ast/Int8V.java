package streamsql.ast;

public final class Int8V implements Numeric<Int8V, Byte> {
    private final byte value;
    public Int8V(byte value) { this.value = value; }
    public byte value() { return value; }
    public Int8V add(Int8V other) { return new Int8V((byte)(this.value + other.value)); }
    public Int8V subtract(Int8V other) { return new Int8V((byte)(this.value - other.value)); }
    public Int8V multiply(Int8V other) { return new Int8V((byte)(this.value * other.value)); }
    public Int8V divide(Int8V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int8V((byte)(this.value / other.value));
    }
}
