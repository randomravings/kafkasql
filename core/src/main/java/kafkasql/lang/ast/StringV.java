package kafkasql.lang.ast;

public final record StringV(Range range, String value) implements AlphaV, Comparable<StringV> {
    @Override
    public int compareTo(StringV o) {
        return this.value.compareTo(o.value);
    }
}
