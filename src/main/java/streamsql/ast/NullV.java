package streamsql.ast;

public final class NullV implements Literal<NullV, Void> {
    public static final NullV INSTANCE = new NullV();
    private NullV() {}
    public static NullV getInstance() { return INSTANCE; }
}
