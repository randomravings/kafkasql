package kafkasql.runtime;

import java.util.HashMap;
import java.util.Map;

public final class Name {
    public static final Name ROOT = new Name("");
    private static final Map<String, Name> CACHE = new HashMap<>();
    private static final String SEPARATOR = ".";
    private final String _fullName;      // Original casing for display/codegen
    private final String _canonicalName; // Lowercase for case-insensitive lookups

    private Name(String fullName) {
        _fullName = fullName;
        _canonicalName = fullName.toLowerCase();
    }

    public static Name of(String context, String name) {
        String fullName = context.isEmpty() ?
            name :
            context + SEPARATOR + name
        ;
        return CACHE.computeIfAbsent(
            fullName, k -> new Name(fullName)
        );
    }

    public static Name of(String name) {;
        return of("", name);
    }

    public String name() {
        int idx = _fullName.lastIndexOf(SEPARATOR);
        if (idx == -1)
            return _fullName;
        else
            return _fullName.substring(idx + 1);
    }

    public String context() {
        int idx = _fullName.lastIndexOf(SEPARATOR);
        if (idx == -1)
            return "";
        else
            return _fullName.substring(0, idx);
    }

    public String fullName() {
        return _fullName;
    }

    public boolean isRoot() {
        return _fullName.isEmpty();
    }

    public Name add(String name) {
        return of(_fullName, name);
    }

    @Override
    public String toString() {
        return _fullName;
    }

    @Override
    public boolean equals(Object o) {
        return switch (o) {
            case Name other -> _canonicalName.equals(other._canonicalName);
            default -> false;
        };
    }

    @Override
    public int hashCode() {
        return _canonicalName.hashCode();
    }
}
