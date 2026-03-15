package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.QName;

public sealed interface DropStmt extends Stmt {

    QName target();

    record DropContext(Range range, QName target) implements DropStmt {}
    record DropType(Range range, QName target) implements DropStmt {}
    record DropStream(Range range, QName target) implements DropStmt {}
}
