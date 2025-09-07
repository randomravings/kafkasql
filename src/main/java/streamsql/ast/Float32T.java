package streamsql.ast;

public final class Float32T implements PrimitiveType {
    private static final Float32T INSTANCE = new Float32T();
    private Float32T() {}
    public static Float32T get() { return INSTANCE; }
}
