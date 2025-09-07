package streamsql.ast;

public final class StringV implements Chars<StringV, String> {
    private final String value;
    public StringV(String value) { this.value = value; }
    public String value() { return value; }
    public StringV concat(StringV other) {
        return new StringV(this.value + other.value());
    }
}
