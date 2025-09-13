package streamsql.ast;

public final class Int32T implements IntegerT {
    private static final Int32T INSTANCE = new Int32T();
    private Int32T() {}
    public static Int32T get() { return INSTANCE; }
}
