package streamsql.ast;

import java.util.HashMap;

public final class FStringT implements PrimitiveType {
    private static final HashMap<Integer, FStringT> TYPES = new HashMap<>();
    private final Int32V size;
    private FStringT(Int32V size) {
        this.size = size;
    }
    public Int32V size() { return size; }
    public static FStringT get(Int32V size) {
        return TYPES.computeIfAbsent(size.value(), k -> new FStringT(size));
    }
}
