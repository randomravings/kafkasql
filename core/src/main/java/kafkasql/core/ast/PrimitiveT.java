package kafkasql.core.ast;

public sealed interface PrimitiveT extends AnyT
    permits BoolT, AlphaT, BinaryT, NumberT, TemporalT { }
