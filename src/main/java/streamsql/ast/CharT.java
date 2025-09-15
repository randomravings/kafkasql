package streamsql.ast;

import java.util.HashMap;

public final class CharT implements AlphaT {
    private static final HashMap<Integer, CharT> TYPES = new HashMap<>();
    private final Int32V size;
    private CharT(Int32V size) {
        this.size = size;
    }
    public Int32V size() { return size; }
    public static CharT get(Int32V size) {
        return get(size.value());
    }
    public static CharT get(int size) {
        return TYPES.computeIfAbsent(size, k -> new CharT(new Int32V(size)));
    }
}
