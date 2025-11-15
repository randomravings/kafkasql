package kafkasql.core.ast;

public final class ListV<T extends AnyV> extends AstListNode<T> implements CompositeV {
    public ListV(Range range) {
        super(range);
    }
}
