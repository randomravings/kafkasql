package streamsql.ast;

import java.util.HashMap;

public final class DecimalT implements PrimitiveType {
    private static final HashMap<Integer, DecimalT> TYPES = new HashMap<>();
    private final Int32V precision;
    private final Int32V scale;
    private DecimalT(Int32V precision, Int32V scale) {
        this.precision = precision;
        this.scale = scale;
    }
    public Int32V precision() { return precision; }
    public Int32V scale() { return scale; }
    public static DecimalT get(Int32V precision, Int32V scale) {
        var key = precision.value() * 1000 + scale.value();
        return TYPES.computeIfAbsent(key, k -> new DecimalT(precision, scale));
    }
}