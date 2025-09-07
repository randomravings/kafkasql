package streamsql.ast;

public final class Float32V implements Numeric<Float32V, Float> {
    private final float value;
    public Float32V(float value) { this.value = value; }
    public float value() { return value; }
    public Float32V add(Float32V other) { return new Float32V(this.value + other.value); }
    public Float32V subtract(Float32V other) { return new Float32V(this.value - other.value); }
    public Float32V multiply(Float32V other) { return new Float32V(this.value * other.value); }
    public Float32V divide(Float32V other) {
        if (other.value == 0.0f) throw new ArithmeticException("Divide by zero");
        return new Float32V(this.value / other.value);
    }
}
