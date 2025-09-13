package streamsql.ast;

public sealed interface PrimitiveType extends AnyT
    permits BoolT, AlphaT, BinaryT, NumberT, TemporalT { }
