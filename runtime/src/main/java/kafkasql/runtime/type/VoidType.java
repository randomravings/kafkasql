package kafkasql.runtime.type;

public final class VoidType implements AnyType {
    private static final VoidType INSTANCE = new VoidType();
    private VoidType() { }
    public static VoidType get() {
        return INSTANCE;
    }
}
