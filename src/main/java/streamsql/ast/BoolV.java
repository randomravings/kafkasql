package streamsql.ast;

public final record BoolV(Range range, Boolean value) implements PrimitiveV { }
