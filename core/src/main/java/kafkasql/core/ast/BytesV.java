package kafkasql.core.ast;

import kafkasql.core.Range;

public final record BytesV(Range range, byte[] value) implements BinaryV { }
