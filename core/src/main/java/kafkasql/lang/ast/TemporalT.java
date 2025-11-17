package kafkasql.lang.ast;

public sealed interface TemporalT extends PrimitiveT 
    permits DateT, TimeT, TimestampT, TimestampTzT { }
