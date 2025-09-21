package kafkasql.core.ast;

import kafkasql.core.Range;

public record UseContext(Range range, QName qname) implements UseStmt {}