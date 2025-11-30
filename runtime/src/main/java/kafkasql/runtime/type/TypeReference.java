package kafkasql.runtime.type;

import java.util.HashMap;
import java.util.Map;

public final class TypeReference implements AnyType {
    private static final Map<String, TypeReference> CACHE = new HashMap<>();
    private final String _fqn;
    private TypeReference(String fqn) {
        this._fqn = fqn;
    }
    public String fqn() { return _fqn; }
    public static TypeReference get(String fqn) {
        return CACHE.computeIfAbsent(fqn, TypeReference::new);
    }
}