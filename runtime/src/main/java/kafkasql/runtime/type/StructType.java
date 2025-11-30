package kafkasql.runtime.type;

import java.util.LinkedHashMap;
import java.util.Optional;
import kafkasql.runtime.Name;

public final record StructType(
    Name fqn,
    LinkedHashMap<String, StructTypeField> fields,
    Optional<String> doc
) implements ComplexType { }
