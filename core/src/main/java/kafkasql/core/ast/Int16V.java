package kafkasql.core.ast;

public final record Int16V(Range range, Short value) implements IntegerV, Comparable<Int16V> {
    @Override
    public int compareTo(Int16V o) {
        return this.value.compareTo(o.value);
    }
}
