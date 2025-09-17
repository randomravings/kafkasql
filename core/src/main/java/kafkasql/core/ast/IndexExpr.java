package kafkasql.core.ast;

public final record IndexExpr(Range range, Expr target, Expr index, AnyT type) implements Expr {
    public IndexExpr withTarget(Expr newTarget) {
        return new IndexExpr(this.range, newTarget, this.index, this.type);
    }
}
