package kafkasql.runtime.type;

import java.util.LinkedHashMap;
import java.util.Optional;
import kafkasql.runtime.Name;

public final record UnionType(
    Name fqn,
    LinkedHashMap<String, UnionTypeMember> members,
    Optional<String> doc
) implements ComplexType { }
