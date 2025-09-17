package kafkasql.core.ast;

public final record BoolV(Range range, Boolean value) implements PrimitiveV { }
