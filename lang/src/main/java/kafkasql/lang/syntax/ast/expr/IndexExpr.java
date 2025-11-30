package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.diagnostics.Range;

public final record IndexExpr(
    Range range,
    Expr target,
    Expr index
) implements Expr {
    public IndexExpr withTarget(Expr target) {
        return new IndexExpr(range, target, index);
    }
}
