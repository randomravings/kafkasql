package kafkasql.runtime.type;

import java.util.Optional;

public record UnionTypeMember(
    String name,
    AnyType typ,
    Optional<String> doc
) { }
