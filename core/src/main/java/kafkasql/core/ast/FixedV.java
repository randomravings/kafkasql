package kafkasql.core.ast;

import kafkasql.core.Range;

public final record FixedV(Range range, byte[] value) implements BinaryV {
    public int size() { return value.length; }
}
