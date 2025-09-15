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
        return get(size.value());
    }
    public static FixedT get(int size) {
        return TYPES.computeIfAbsent(size, k -> new FixedT(new Int32V(size)));
    }
}