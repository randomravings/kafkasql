package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Float64V(Double value) implements FractionalV {
    public Float64V add(Float64V other) { return new Float64V(this.value + other.value); }
    public Float64V subtract(Float64V other) { return new Float64V(this.value - other.value); }
    public Float64V multiply(Float64V other) { return new Float64V(this.value * other.value); }
    public Float64V divide(Float64V other) {
        if (other.value == 0.0) throw new ArithmeticException("Divide by zero");
        return new Float64V(this.value / other.value);
    }
}
