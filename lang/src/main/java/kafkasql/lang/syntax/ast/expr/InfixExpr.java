package kafkasql.lang.syntax.ast.expr;

import kafkasql.runtime.diagnostics.Range;

public record InfixExpr(
    Range range,
    InfixOp op,
    Expr left,
    Expr right
) implements Expr { }
