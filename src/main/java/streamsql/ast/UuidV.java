package streamsql.ast;

import java.util.UUID;

public final record UuidV(UUID value) implements Literal<UuidV, UUID> { }
