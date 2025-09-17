package streamsql.ast;

public final class ExprList extends AstListNode<Expr> {
    public ExprList(Range range) {
        super(range);
    }
    public ExprList(Range range, java.util.List<Expr> items) {
        super(range, items);
    }    
}
