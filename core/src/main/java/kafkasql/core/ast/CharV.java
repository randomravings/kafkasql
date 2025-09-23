package kafkasql.core.ast;

import kafkasql.core.Range;

public final record CharV(Range range, String value) implements AlphaV {
    public int size() { return value.length(); }
}
