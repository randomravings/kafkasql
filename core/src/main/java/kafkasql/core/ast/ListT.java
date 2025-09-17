package kafkasql.core.ast;

public record ListT(Range range, AnyT item) implements CompositeT { }
