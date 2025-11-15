package kafkasql.core.ast;

public final record Int64V(Range range, Long value) implements IntegerV, Comparable<Int64V> {
    @Override
    public int compareTo(Int64V o) {
        return this.value.compareTo(o.value);
    }
}
