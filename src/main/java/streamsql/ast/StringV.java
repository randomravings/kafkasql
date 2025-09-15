package streamsql.ast;

@SuppressWarnings("unchecked")
public final record StringV(String value) implements AlphaV {
    public AnyT type() { return StringT.get(); }
}
