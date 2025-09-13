package streamsql.ast;

import java.util.HashMap;

public final class DecimalT implements FractionalT {
    private static final HashMap<Integer, DecimalT> TYPES = new HashMap<>();
    private final Int8V precision;
    private final Int8V scale;
    private DecimalT(Int8V precision, Int8V scale) {
        this.precision = precision;
        this.scale = scale;
    }
    public Int8V precision() { return precision; }
    public Int8V scale() { return scale; }
    public static DecimalT get(Int8V precision, Int8V scale) {
        var key = precision.value() * 1000 + scale.value();
        return TYPES.computeIfAbsent(key, k -> new DecimalT(precision, scale));
    }
}