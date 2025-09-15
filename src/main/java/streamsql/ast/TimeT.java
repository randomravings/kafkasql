package streamsql.ast;

import java.util.HashMap;

public final class TimeT implements TemporalT {
    private static final HashMap<Byte, TimeT> TYPES = new HashMap<>();
    private final Int8V precision;
    private TimeT(Int8V precision) {
        this.precision = precision;
    }
    public Int8V precision() { return precision; }
    public static TimeT get(Int8V precision) {
        return get(precision.value());
    }
    public static TimeT get(byte precision) {
        return TYPES.computeIfAbsent(precision, k -> new TimeT(new Int8V(precision)));
    }
}
