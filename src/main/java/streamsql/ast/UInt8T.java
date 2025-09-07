package streamsql.ast;

public final class UInt8T implements PrimitiveType {
    private static final UInt8T INSTANCE = new UInt8T();
    private UInt8T() {}
    public static UInt8T get() { return INSTANCE; }
}
