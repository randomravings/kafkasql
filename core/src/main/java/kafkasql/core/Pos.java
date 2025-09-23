package kafkasql.core;

public final record Pos(int ln, int ch) {
    public static final Pos NONE = new Pos(-1, -1);
}
