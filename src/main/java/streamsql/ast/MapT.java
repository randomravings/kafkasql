package streamsql.ast;

public record MapT(AnyT key, AnyT value) implements CompositeT { }
