package kafkasql.core.ast;

import java.util.UUID;

import kafkasql.core.Range;

public final record UuidV(Range range, UUID value) implements AlphaV { }
