package kafkasql.lang.ast;

public final record Float32V(Range range, Float value) implements FractionalV, Comparable<Float32V> {
    @Override
    public int compareTo(Float32V o) {
        return this.value.compareTo(o.value);
    }
}
