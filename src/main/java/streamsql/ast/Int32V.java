package streamsql.ast;

public final class Int32V implements Numeric<Int32V, Integer> {
    private final int value;
    public Int32V(int value) { this.value = value; }
    public int value() { return value; }
    public Int32V add(Int32V other) { return new Int32V(this.value + other.value); }
    public Int32V subtract(Int32V other) { return new Int32V(this.value - other.value); }
    public Int32V multiply(Int32V other) { return new Int32V(this.value * other.value); }
    public Int32V divide(Int32V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int32V(this.value / other.value);
    }
}
