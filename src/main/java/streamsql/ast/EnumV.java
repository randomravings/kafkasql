package streamsql.ast;

public final record EnumV(Range range, Identifier enumName, Identifier symbol) implements ComplexV { }
