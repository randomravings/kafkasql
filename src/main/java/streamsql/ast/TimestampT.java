package streamsql.ast;

import java.util.HashMap;

public final class TimestampT implements TemporalT {
    private static final HashMap<Byte, TimestampT> TYPES = new HashMap<>();
    private final Int8V precision;
    private TimestampT(Int8V precision) {
        this.precision = precision;
    }
    public Int8V precision() { return precision; }
    public static TimestampT get(Int8V precision) {
        return get(precision.value());
    }
    public static TimestampT get(byte precision) {
        return TYPES.computeIfAbsent(precision, k -> new TimestampT(new Int8V(precision)));
    }
}
