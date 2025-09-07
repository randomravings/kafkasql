package streamsql.ast;

public final class Int8T implements PrimitiveType {
    private static final Int8T INSTANCE = new Int8T();
    private Int8T() {}
    public static Int8T get() { return INSTANCE;}
}