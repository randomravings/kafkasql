package kafkasql.core.ast;

public final record Float64V(Range range, Double value) implements FractionalV, Comparable<Float64V> {
    @Override
    public int compareTo(Float64V o) {
        return this.value.compareTo(o.value);
    }
}
