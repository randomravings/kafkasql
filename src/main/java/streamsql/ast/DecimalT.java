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
        return get(precision.value(), scale.value());
    }
    public static DecimalT get(Byte precision, Byte scale) {
        var key = precision * 1000 + scale;
        return TYPES.computeIfAbsent(key, k -> new DecimalT(new Int8V(precision), new Int8V(scale)));
    }
}