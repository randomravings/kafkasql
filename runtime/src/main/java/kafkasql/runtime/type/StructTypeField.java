package kafkasql.runtime.type;

import java.util.Optional;

public final record StructTypeField(
    String name,
    AnyType type,
    boolean nullable,
    Optional<Object> defaultValue,
    Optional<String> doc
) { }
