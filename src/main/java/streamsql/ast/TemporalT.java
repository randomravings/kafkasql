package streamsql.ast;

public sealed interface TemporalT extends PrimitiveType 
    permits DateT, TimeT, TimestampT, TimestampTzT { }
