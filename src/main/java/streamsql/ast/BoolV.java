package streamsql.ast;

public final class BoolV implements Literal<BoolV, Boolean> {
    private final boolean value;
    public BoolV(boolean value) { this.value = value; }
    public boolean value() { return value; }
    public BoolV and(BoolV other) { return new BoolV(this.value && other.value); }
    public BoolV or(BoolV other) { return new BoolV(this.value || other.value); }
    public BoolV xor(BoolV other) { return new BoolV(this.value ^ other.value); }
    public BoolV not() { return new BoolV(!this.value); }
}
