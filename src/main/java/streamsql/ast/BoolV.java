package streamsql.ast;

@SuppressWarnings("unchecked")
public final record BoolV(Boolean value) implements PrimitiveV {
    public AnyT type() { return BoolT.get(); }
    public BoolV and(BoolV other) { return new BoolV(this.value && other.value); }
    public BoolV or(BoolV other) { return new BoolV(this.value || other.value); }
    public BoolV xor(BoolV other) { return new BoolV(this.value ^ other.value); }
    public BoolV not() { return new BoolV(!this.value); }
}
