package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Int32V(Integer value) implements IntegerV {
    public static final Int32V ZERO = new Int32V(0);
    public AnyT type() { return Int32T.get(); }
    public Int32V add(Int32V other) { return new Int32V(this.value + other.value); }
    public Int32V subtract(Int32V other) { return new Int32V(this.value - other.value); }
    public Int32V multiply(Int32V other) { return new Int32V(this.value * other.value); }
    public Int32V divide(Int32V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int32V(this.value / other.value);
    }
}
