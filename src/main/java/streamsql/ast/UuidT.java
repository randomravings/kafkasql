package streamsql.ast;

public final class UuidT implements AlphaT {
    private static final UuidT INSTANCE = new UuidT();
    private UuidT() {}
    public static UuidT get() { return INSTANCE; }
}
