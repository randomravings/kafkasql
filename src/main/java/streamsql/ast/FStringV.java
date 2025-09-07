package streamsql.ast;

public final class FStringV implements Chars<FStringV, String> {
    private final String value;
    private final int length;
    public FStringV(String value, int length) {
        this.value = value;
        if (value.length() > length)
            throw new IllegalArgumentException("FSTRING length exceeds defined limit: " + length);
        this.length = length;
    }
    public String value() { return value; }
    public int length() { return length; }
    public FStringV concat(FStringV other) {
        return new FStringV(this.value + other.value(), this.length + other.length());
    }
}
