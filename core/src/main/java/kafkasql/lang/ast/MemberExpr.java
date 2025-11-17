package kafkasql.lang.ast;

public final record MemberExpr(Range range, Expr target, Identifier name, AnyT type) implements Expr {
    public MemberExpr withTarget(Expr target) {
        return new MemberExpr(range, target, name, type);
    }
}
