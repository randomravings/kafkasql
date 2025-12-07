package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.QName;

public record ExplainStmt(
    Range range,
    QName target
) implements Stmt { }
