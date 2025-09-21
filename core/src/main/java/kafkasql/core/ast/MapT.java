package kafkasql.core.ast;

import kafkasql.core.Range;

public record MapT(Range range, AnyT key, AnyT value) implements CompositeT { }
