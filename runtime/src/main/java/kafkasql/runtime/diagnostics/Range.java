package kafkasql.runtime.diagnostics;

public record Range(
    String source,
    Pos from,
    Pos to
) {
    public static final Range NONE = new Range("", Pos.NONE, Pos.NONE);

    public Range(String source, int lns, int chs, int lne, int che) {
        this(source, new Pos(lns, chs), new Pos(lne, che));
    }

    public static Range merge(Range ul, Range br) {
        return new Range(ul.source(), ul.from(), br.to());
    }

    @Override
    public String toString() {
        if (this == NONE) return "";
        return source + ":" + from + "-" + to;
    }
}