package kafkasql.runtime.type;

import java.util.List;
import java.util.Optional;
import kafkasql.runtime.Name;

public record EnumType(
    Name fqn,
    PrimitiveKind type,
    List<EnumTypeSymbol> symbols,
    Optional<String> doc
) implements ComplexType { }
