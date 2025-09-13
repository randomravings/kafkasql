package streamsql.ast;

import java.util.HashMap;

public final class FixedT implements BinaryT {
    private static final HashMap<Integer, FixedT> TYPES = new HashMap<>();
    private final Int32V size;
    private FixedT(Int32V size) {
        this.size = size;
    }
    public Int32V size() { return size; }
    public static FixedT get(Int32V size) {
        return TYPES.computeIfAbsent(size.value(), k -> new FixedT(size));
    }
}