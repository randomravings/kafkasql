package streamsql.ast;

public final class Float64T implements PrimitiveType {
    private static final Float64T INSTANCE = new Float64T();
    private Float64T() {}
    public static Float64T get() { return INSTANCE; }
}
