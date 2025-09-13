package streamsql.ast;

public final class VoidT implements AnyT {
    private static final VoidT INSTANCE = new VoidT();
    private VoidT() {}
    public static VoidT get() { return INSTANCE; }
}
