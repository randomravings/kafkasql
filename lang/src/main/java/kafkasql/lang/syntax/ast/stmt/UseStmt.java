package kafkasql.lang.syntax.ast.stmt;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.use.UseTarget;

public final record UseStmt(
    Range range,
    UseTarget target
) implements Stmt { }
