package kafkasql.runtime.type;

import java.util.Optional;

public final record StructTypeField(
    String name,
    AnyType type,
    boolean nullable,
    boolean dropped,
    Optional<Object> defaultValue,
    Optional<String> doc
) {
    /**
     * Convenience constructor — dropped defaults to false.
     */
    public StructTypeField(
        String name,
        AnyType type,
        boolean nullable,
        Optional<Object> defaultValue,
        Optional<String> doc
    ) {
        this(name, type, nullable, false, defaultValue, doc);
    }

    /**
     * Returns a copy of this field with the dropped flag set.
     */
    public StructTypeField withDropped(boolean dropped) {
        return new StructTypeField(name, type, nullable, dropped, defaultValue, doc);
    }
}
