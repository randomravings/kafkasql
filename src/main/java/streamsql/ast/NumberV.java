package streamsql.ast;

public sealed interface NumberV extends Literal
    permits IntegerV, FractionalV { }
