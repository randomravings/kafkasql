package streamsql.ast;

public final class Int64V implements Numeric<Int64V, Long> {
    private final long value;
    public Int64V(long value) { this.value = value; }
    public long value() { return value; }
    public Int64V add(Int64V other) { return new Int64V(this.value + other.value); }
    public Int64V subtract(Int64V other) { return new Int64V(this.value - other.value); }
    public Int64V multiply(Int64V other) { return new Int64V(this.value * other.value); }
    public Int64V divide(Int64V other) {
        if (other.value == 0) throw new ArithmeticException("Divide by zero");
        return new Int64V(this.value / other.value);
    }
}
