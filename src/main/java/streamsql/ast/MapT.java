package streamsql.ast;

public record MapT(Range range, AnyT key, AnyT value) implements CompositeT { }
