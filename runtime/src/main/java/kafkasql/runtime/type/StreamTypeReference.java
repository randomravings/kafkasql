package kafkasql.runtime.type;

import java.util.Optional;

public final record StreamTypeReference(
    String alias,
    StructType struct,
    Optional<StructTypeField[]> distributeFields,
    Optional<StructTypeField> timestampField
) implements StreamTypeKind { }
