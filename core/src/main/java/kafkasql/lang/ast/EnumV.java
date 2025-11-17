package kafkasql.lang.ast;

public final record EnumV(Range range, QName enumName, Identifier symbol) implements ComplexV { }
