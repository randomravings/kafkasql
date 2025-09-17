package kafkasql.core.ast;

public sealed interface IntegerV extends NumberV
    permits Int8V, Int16V, Int32V, Int64V { }
