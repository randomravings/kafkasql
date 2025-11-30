package kafkasql.runtime.type;

import java.util.Optional;

public sealed interface StreamTypeKind
    permits StreamTypeInline, StreamTypeReference {
    String alias();
    Optional<StructTypeField[]> distributeFields();
}
