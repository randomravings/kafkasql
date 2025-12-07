package kafkasql.lang.syntax.ast.expr;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record MemberExpr(
    Range range,
    Expr target,
    Identifier name
) implements Expr {
    public MemberExpr withTarget(Expr target) {
        return new MemberExpr(range, target, name);
    }
}
