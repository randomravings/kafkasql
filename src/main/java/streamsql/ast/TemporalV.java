package streamsql.ast;

public sealed interface TemporalV extends PrimitiveV permits DateV, TimeV, TimestampV, TimestampTzV { }
