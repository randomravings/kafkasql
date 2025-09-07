package streamsql.ast;

public final class DateT implements PrimitiveType {
    private static final DateT INSTANCE = new DateT();
    private DateT() {}
    public static DateT get() { return INSTANCE; }
}
