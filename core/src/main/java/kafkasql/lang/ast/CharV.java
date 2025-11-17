package kafkasql.lang.ast;

public final record CharV(Range range, String value) implements AlphaV, Comparable<CharV> {
    public int size() { return value.length(); }
    @Override
    public int compareTo(CharV o) {
        return this.value.compareTo(o.value);
    }
}
