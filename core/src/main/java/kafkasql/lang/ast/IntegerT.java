package kafkasql.lang.ast;

public sealed interface IntegerT extends NumberT
    permits Int8T,  Int16T,  Int32T,  Int64T { }
