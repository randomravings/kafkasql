package kafkasql.core.ast;

public record UseContext(Range range, QName qname) implements UseStmt {}