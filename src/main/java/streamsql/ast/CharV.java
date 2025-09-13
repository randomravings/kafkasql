package streamsql.ast;

@SuppressWarnings("unchecked")
public final record CharV(String value) implements AlphaV {
    public Int32V length() { return new Int32V(value.length()); }
    public CharV concat(CharV other) {
        return new CharV(this.value + other.value());
    }
}
