package streamsql.ast;

import java.util.HashMap;

public final class FBytesT implements PrimitiveType {
    private static final HashMap<Integer, FBytesT> TYPES = new HashMap<>();
    private final Int32V size;
    private FBytesT(Int32V size) {
        this.size = size;
    }
    public Int32V size() { return size; }
    public static FBytesT get(Int32V size) {
        return TYPES.computeIfAbsent(size.value(), k -> new FBytesT(size));
    }
}