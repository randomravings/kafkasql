package kafkasql.lang.syntax.ast.expr;

import kafkasql.runtime.diagnostics.Range;

public final record TrifixExpr(
    Range range,
    TernaryOp op,
    Expr left,
    Expr middle,
    Expr right
) implements Expr { }
