package streamsql.ast;

public final class UInt32T implements PrimitiveType {
    private static final UInt32T INSTANCE = new UInt32T();
    private UInt32T() {}
    public static UInt32T get() { return INSTANCE; }
}
