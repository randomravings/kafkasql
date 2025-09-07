package streamsql.ast;

public final class BoolT implements PrimitiveType {
    private static final BoolT INSTANCE = new BoolT();
    private BoolT() {}
    public static BoolT get() { return INSTANCE; }
}
