package kafkasql.core.ast;

import kafkasql.core.Range;

public final record CharT(Range range, int size) implements AlphaT { }
