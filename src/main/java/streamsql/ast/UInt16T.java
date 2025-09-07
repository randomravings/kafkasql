package streamsql.ast;

public final class UInt16T implements PrimitiveType {
    private static final UInt16T INSTANCE = new UInt16T();
    private UInt16T() {}
    public static UInt16T get() { return INSTANCE; }
}
