package streamsql.ast;

public final class Int16T implements IntegerT {
    private static final Int16T INSTANCE = new Int16T();
    private Int16T() {}
    public static Int16T get() { return INSTANCE; }
}
