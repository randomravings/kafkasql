package kafkasql.runtime.type;

import java.util.Optional;
import kafkasql.runtime.Name;

public record ScalarType(
    Name fqn,
    PrimitiveType primitive,
    Optional<Object> defaultValue,
    Optional<String> doc
) implements ComplexType {}
