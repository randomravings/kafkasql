package streamsql.ast;

import java.util.List;

public final record Tuple (List<?extends Literal<?, ?>> values) {
    public int arity() {
        return values.size();
    }
}
