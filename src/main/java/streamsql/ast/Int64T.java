package streamsql.ast;

public final class Int64T implements PrimitiveType {
    private static final Int64T INSTANCE = new Int64T();
    private Int64T() {}
    public static Int64T get() { return INSTANCE; }
}
