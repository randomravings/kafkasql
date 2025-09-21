package kafkasql.core.ast;

import kafkasql.core.Range;

public record ListT(Range range, AnyT item) implements CompositeT { }
