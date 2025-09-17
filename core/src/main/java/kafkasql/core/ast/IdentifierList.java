package kafkasql.core.ast;

import java.util.List;

public final class IdentifierList extends AstListNode<Identifier> {
    public static final IdentifierList EMPTY = new IdentifierList(Range.NONE);
    public IdentifierList(Range range) {
        super(range);
    }
    public IdentifierList(Range range, List<Identifier> items) {
        super(range, items);
    }
}
