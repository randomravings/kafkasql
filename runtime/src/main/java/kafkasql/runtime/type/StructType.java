package kafkasql.runtime.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import kafkasql.runtime.Name;

public final record StructType(
    Name fqn,
    LinkedHashMap<String, StructTypeField> fields,
    List<CheckConstraint> constraints,
    Optional<String> doc
) implements ComplexType { }
