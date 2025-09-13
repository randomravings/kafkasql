package streamsql.ast;

public final class BytesT implements BinaryT {
    private static final BytesT INSTANCE = new BytesT();
    private BytesT() {}
    public static BytesT get() { return INSTANCE; }
}
