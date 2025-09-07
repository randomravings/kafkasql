package streamsql.ast;

import java.util.HashMap;

public final class TimestampTzT implements PrimitiveType {
    private static final HashMap<Integer, TimestampTzT> TYPES = new HashMap<>();
    private final Int32V precision;
    private TimestampTzT(Int32V precision) {
        this.precision = precision;
    }
    public Int32V precision() { return precision; }
    public static TimestampTzT get(Int32V precision) {
        return TYPES.computeIfAbsent(precision.value(), k -> new TimestampTzT(precision));
    }
}
