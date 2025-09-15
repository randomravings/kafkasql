package streamsql.ast;

import java.util.Map;

public final record MapV (Map<PrimitiveV, AnyV> values) implements CompositeV {
    public AnyT type() { return new MapT(VoidT.get(), VoidT.get()); }
}
