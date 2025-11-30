package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.diagnostics.Range;

public final record PrefixExpr(
    Range range,
    PrefixOp op,
    Expr expr
) implements Expr { }
