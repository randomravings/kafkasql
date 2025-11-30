package kafkasql.runtime.type;

import java.util.List;
import java.util.Optional;

public final record StreamTypeInline(
    String alias,
    List<StructTypeField> fields,
    Optional<StructTypeField[]> distributeFields
) implements StreamTypeKind { }
