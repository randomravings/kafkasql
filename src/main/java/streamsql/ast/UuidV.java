package streamsql.ast;

import java.util.UUID;

public final record UuidV(Range range, UUID value) implements AlphaV { }
