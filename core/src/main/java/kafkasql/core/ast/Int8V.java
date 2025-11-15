package kafkasql.core.ast;

public final record Int8V(Range range, Byte value) implements IntegerV, Comparable<Int8V> {
    @Override
    public int compareTo(Int8V o) {
        return this.value.compareTo(o.value);
    }
}
