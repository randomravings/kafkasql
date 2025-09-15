package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Int16V(Short value) implements IntegerV {
    public static final Int16V ZERO = new Int16V((short)0);
    public AnyT type() { return Int16T.get(); }
    public Int16V add(Int16V other) { return new Int16V((short)(this.value + other.value)); }
    public Int16V subtract(Int16V other) { return new Int16V((short)(this.value - other.value)); }
    public Int16V multiply(Int16V other) { return new Int16V((short)(this.value * other.value)); }
    public Int16V divide(Int16V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int16V((short)(this.value / other.value));
    }
}
