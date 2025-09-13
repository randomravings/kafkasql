package streamsql.ast;

public record MapT(PrimitiveType key, AnyT value) implements CompositeType {}
