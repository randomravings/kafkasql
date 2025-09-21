package kafkasql.core.ast;

import java.util.List;

import kafkasql.core.Range;

public final class ListV extends AstListNode<AnyV> implements CompositeV {
    public ListV(Range range) {
        super(range);
    }
    public ListV(Range range, List<AnyV> other) {
        super(range, other);
    }
}
