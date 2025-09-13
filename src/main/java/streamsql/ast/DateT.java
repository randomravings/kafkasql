package streamsql.ast;

public final class DateT implements TemporalT {
    private static final DateT INSTANCE = new DateT();
    private DateT() {}
    public static DateT get() { return INSTANCE; }
}
