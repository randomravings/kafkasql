package kafkasql.core.ast;

public sealed interface NumberV extends PrimitiveV
    permits IntegerV, FractionalV { }
