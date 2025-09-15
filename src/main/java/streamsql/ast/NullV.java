package streamsql.ast;

public final class NullV implements AnyV {
    public static final NullV INSTANCE = new NullV();
    public AnyT type() { return VoidT.get(); }
    private NullV() {}
    public static NullV getInstance() { return INSTANCE; }
}
