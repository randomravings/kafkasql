package streamsql.ast;

public final class EnumV implements Literal {
    private final Identifier symbol;
    public EnumV(Identifier symbol) { this.symbol = symbol; }
    public Identifier symbol() { return symbol; }
}
