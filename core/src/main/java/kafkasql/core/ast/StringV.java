package kafkasql.core.ast;

import kafkasql.core.Range;

public final record StringV(Range range, String value) implements AlphaV { }
