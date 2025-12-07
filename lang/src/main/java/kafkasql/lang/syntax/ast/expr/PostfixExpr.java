package kafkasql.lang.syntax.ast.expr;

import kafkasql.runtime.diagnostics.Range;

public final record PostfixExpr(
    Range range,
    PostfixOp op,
    Expr expr
) implements Expr { }
