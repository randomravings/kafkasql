package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Int8V(Byte value) implements IntegerV {
    public static final Int8V ZERO = new Int8V((byte)0);
    public AnyT type() { return Int8T.get(); }
    public Int8V add(Int8V other) { return new Int8V((byte)(this.value + other.value)); }
    public Int8V subtract(Int8V other) { return new Int8V((byte)(this.value - other.value)); }
    public Int8V multiply(Int8V other) { return new Int8V((byte)(this.value * other.value)); }
    public Int8V divide(Int8V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int8V((byte)(this.value / other.value));
    }
}
