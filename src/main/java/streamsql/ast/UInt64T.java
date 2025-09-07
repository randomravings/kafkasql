package streamsql.ast;

public final class UInt64T implements PrimitiveType {
    private static final UInt64T INSTANCE = new UInt64T();
    private UInt64T() {}
    public static UInt64T get() { return INSTANCE; }
}
