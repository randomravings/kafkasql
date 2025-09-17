package streamsql.ast;

import java.util.Map;

public final class StructV extends AstMapNode<Identifier, AnyV> implements ComplexV {
    public StructV(Range range) {
        super(range);
    }

    public StructV(Range range, Map<Identifier, AnyV> other) {
        super(range, other);
    }
}
