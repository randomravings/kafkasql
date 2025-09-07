package streamsql.ast;

public final class Int16V implements Numeric<Int16V, Short> {
    private final short value;
    public Int16V(short value) { this.value = value; }
    public short value() { return value; }
    public Int16V add(Int16V other) { return new Int16V((short)(this.value + other.value)); }
    public Int16V subtract(Int16V other) { return new Int16V((short)(this.value - other.value)); }
    public Int16V multiply(Int16V other) { return new Int16V((short)(this.value * other.value)); }
    public Int16V divide(Int16V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int16V((short)(this.value / other.value));
    }
}
