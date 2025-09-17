package kafkasql.core.ast;

public sealed interface TemporalT extends PrimitiveT 
    permits DateT, TimeT, TimestampT, TimestampTzT { }
