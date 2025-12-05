package kafkasql.runtime.type;

import java.util.List;
import java.util.Optional;

public final record StreamTypeInline(
    String alias,
    List<StructTypeField> fields,
    Optional<StructTypeField[]> distributeFields,
    Optional<StructTypeField> timestampField
) implements StreamTypeKind { }
