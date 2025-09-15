package streamsql.ast;

import java.util.List;

public final record ListV (List<AnyV> values) implements CompositeV {
    public AnyT type() { return new ListT(VoidT.get()); }
}
