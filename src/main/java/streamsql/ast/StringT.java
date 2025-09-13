package streamsql.ast;

public final class StringT implements AlphaT {
    private static final StringT INSTANCE = new StringT();
    private StringT() {}
    public static StringT get() { return INSTANCE; }
}
