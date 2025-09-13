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
        return TYPES.computeIfAbsent(precision.value(), k -> new TimeT(precision));
    }
}
