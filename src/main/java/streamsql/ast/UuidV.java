package streamsql.ast;

import java.util.UUID;

@SuppressWarnings("unchecked")
public final record UuidV(UUID value) implements AlphaV { }
