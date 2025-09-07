package streamsql.ast;

import java.util.HashMap;

public final class TimestampT implements PrimitiveType {
    private static final HashMap<Integer, TimestampT> TYPES = new HashMap<>();
    private final Int32V precision;
    private TimestampT(Int32V precision) {
        this.precision = precision;
    }
    public Int32V precision() { return precision; }
    public static TimestampT get(Int32V precision) {
        return TYPES.computeIfAbsent(precision.value(), k -> new TimestampT(precision));
    }
}
