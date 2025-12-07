package kafkasql.lang.syntax.ast.expr;

import kafkasql.runtime.diagnostics.Range;

public record ParenExpr(
    Range range,
    Expr inner
) implements Expr { }
