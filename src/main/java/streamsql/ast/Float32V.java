package streamsql.ast;

@SuppressWarnings("unchecked")
public final record Float32V(Float value) implements FractionalV {
    public Float32V add(Float32V other) { return new Float32V(this.value + other.value); }
    public Float32V subtract(Float32V other) { return new Float32V(this.value - other.value); }
    public Float32V multiply(Float32V other) { return new Float32V(this.value * other.value); }
    public Float32V divide(Float32V other) {
        if (other.value == 0.0f) throw new ArithmeticException("Divide by zero");
        return new Float32V(this.value / other.value);
    }
}
