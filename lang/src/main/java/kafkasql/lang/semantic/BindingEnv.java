package kafkasql.lang.semantic;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BindingEnv
 *
 * Thin wrapper over IdentityHashMap<Object,Object> used to store
 * semantic bindings (AST node -> semantic payload).
 *
 * You can pass it anywhere a Map<Object,Object> is expected.
 * Over time we can add domain-specific helpers here.
 */
public final class BindingEnv extends IdentityHashMap<Object, Object> {

    public BindingEnv() {
        super();
    }

    /**
     * Typed lookup with Optional.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Object key, Class<T> type) {
        Object value = super.get(key);
        if (value == null || !type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    /**
     * Typed lookup with null on mismatch.
     * (Useful for quick & dirty debugging.)
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrNull(Object key, Class<T> type) {
        Object value = super.get(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return (T) value;
    }

    /**
     * Expose as plain Map when you really need it.
     * (Mostly for legacy code / external APIs.)
     */
    public Map<Object,Object> asMap() {
        return this;
    }
}