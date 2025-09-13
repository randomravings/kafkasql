package streamsql.ast;

public final class NullV implements Literal {
    public static final NullV INSTANCE = new NullV();
    private NullV() {}
    public static NullV getInstance() { return INSTANCE; }
    @SuppressWarnings("unchecked")
    @Override
    public Void value() {
        return null;
    }
}
