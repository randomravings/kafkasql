package kafkasql.core.ast;

import java.util.List;

public final class Ast extends AstListNode<Stmt> {
    public static final Ast EMPTY = new Ast(Range.NONE, List.of());
    public Ast() {
        this(Range.NONE, List.of());
    }
    public Ast(Range range, List<Stmt> statements) {
        super(Range.NONE, statements);
    }
}
