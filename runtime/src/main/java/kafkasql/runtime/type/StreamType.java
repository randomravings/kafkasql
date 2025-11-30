package kafkasql.runtime.type;

import java.util.LinkedHashMap;
import java.util.Optional;

public final record StreamType(
    String fqn,
    LinkedHashMap<String, StreamTypeKind> types,
    Optional<String[]> doc
) { }
