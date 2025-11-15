package kafkasql.core.ast;

public final class MapV<K extends PrimitiveV, V extends AnyV> extends AstMapNode<K, V> implements CompositeV {
    public MapV(Range range) {
        super(range);
    }
}
