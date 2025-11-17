package kafkasql.lang.ast;

public record ListT(Range range, AnyT item) implements CompositeT { }
