package streamsql.ast;

public record UseContext(Range range, QName qname) implements UseStmt {}