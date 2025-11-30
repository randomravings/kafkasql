package kafkasql.runtime.type;

import java.util.Optional;

public record EnumTypeSymbol(
    String name,
    long value,
    Optional<String> doc
) { }
