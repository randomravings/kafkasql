package kafkasql.core.ast;

import kafkasql.core.Range;

public final record MemberExpr(Range range, Expr target, Identifier name, AnyT type) implements Expr {
    public MemberExpr withTarget(Expr target) {
        return new MemberExpr(range, target, name, type);
    }
}
