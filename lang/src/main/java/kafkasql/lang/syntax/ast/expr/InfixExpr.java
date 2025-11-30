package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.diagnostics.Range;

public record InfixExpr(
    Range range,
    InfixOp op,
    Expr left,
    Expr right
) implements Expr { }
