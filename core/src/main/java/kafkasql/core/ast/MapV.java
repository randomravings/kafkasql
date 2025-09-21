package kafkasql.core.ast;

import java.util.Map;

import kafkasql.core.Range;

public final class MapV extends AstMapNode<PrimitiveV, AnyV> implements CompositeV {
    public MapV(Range range) {
        super(range);
    }
    public MapV(Range range, Map<PrimitiveV, AnyV> other) {
        super(range, other);
    }
}
