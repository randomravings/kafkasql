package streamsql.ast;

public sealed interface TemporalV extends Literal permits DateV, TimeV, TimestampV, TimestampTzV { }
