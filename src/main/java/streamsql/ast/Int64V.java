package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Int64V(Long value) implements IntegerV {
    public static final Int64V ZERO = new Int64V(0L);
    public AnyT type() { return Int64T.get(); }
    public Int64V add(Int64V other) { return new Int64V(this.value + other.value); }
    public Int64V subtract(Int64V other) { return new Int64V(this.value - other.value); }
    public Int64V multiply(Int64V other) { return new Int64V(this.value * other.value); }
    public Int64V divide(Int64V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int64V(this.value / other.value);
    }
}
