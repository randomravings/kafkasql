package kafkasql.core.ast;

public final record Pos(int ln, int ch) {
    public static final Pos NONE = new Pos(-1, -1);
}
