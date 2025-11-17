package kafkasql.lang.ast;

public final record Int32V(Range range, Integer value) implements IntegerV, Comparable<Int32V> {
    @Override
    public int compareTo(Int32V o) {
        return this.value.compareTo(o.value);
    }
}
