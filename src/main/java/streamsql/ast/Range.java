package streamsql.ast;

public final record Range(Pos start, Pos end) {
    public static final Range NONE = new Range(Pos.NONE, Pos.NONE);
    public static Range merge(Range ul, Range br) {
        return new Range(ul.start(), br.end());
    }
}
