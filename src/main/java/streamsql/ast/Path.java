package streamsql.ast;

import java.util.List;

public final record Path(List<PathSeg> segments) implements Literal<Path, List<PathSeg>> {
    public String fullName() {
        if(segments.isEmpty()) return "";
        var b = new StringBuilder();
        b.append(segToString(segments.get(0)));
        for(int i = 1; i < segments.size(); i++) {
            var seg = segments.get(i);
            b.append(".").append(segToString(seg));
        }
        return b.toString();
    }

    private static String segToString(PathSeg segment) {
        return switch (segment) {
            case PathFieldSeg f -> f.name().value();
            case PathIndexSeg i -> "[" + i.index() + "]";
            case PathKeySeg k -> "[\"" + k.key().replace("\"", "\\\"") + "\"]";
        };
    }
}
