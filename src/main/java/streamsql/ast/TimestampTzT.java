package streamsql.ast;

import java.util.HashMap;

public final class TimestampTzT implements TemporalT {
    private static final HashMap<Byte, TimestampTzT> TYPES = new HashMap<>();
    private final Int8V precision;
    private TimestampTzT(Int8V precision) {
        this.precision = precision;
    }
    public Int8V precision() { return precision; }
    public static TimestampTzT get(Int8V precision) {
        return TYPES.computeIfAbsent(precision.value(), k -> new TimestampTzT(precision));
    }
}
