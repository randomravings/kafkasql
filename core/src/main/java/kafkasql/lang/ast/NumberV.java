package kafkasql.lang.ast;

public sealed interface NumberV extends PrimitiveV
    permits IntegerV, FractionalV { }
