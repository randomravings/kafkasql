package streamsql.ast;

public final record ScalarV(Range range, PrimitiveV value) implements ComplexV { }
