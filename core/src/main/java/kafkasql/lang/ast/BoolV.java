package kafkasql.lang.ast;

public final record BoolV(Range range, Boolean value) implements PrimitiveV, Comparable<BoolV> {
    @Override
    public int compareTo(BoolV o) {
        return this.value.compareTo(o.value);
    }
}
