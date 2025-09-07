package streamsql.ast;

import java.util.HashMap;

public final class TimeT implements PrimitiveType {
    private static final HashMap<Integer, TimeT> TYPES = new HashMap<>();
    private final Int32V precision;
    private TimeT(Int32V precision) {
        this.precision = precision;
    }
    public Int32V precision() { return precision; }
    public static TimeT get(Int32V precision) {
        return TYPES.computeIfAbsent(precision.value(), k -> new TimeT(precision));
    }
}
