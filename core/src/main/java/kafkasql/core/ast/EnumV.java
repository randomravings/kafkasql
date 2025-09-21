package kafkasql.core.ast;

import kafkasql.core.Range;

public final record EnumV(Range range, QName enumName, Identifier symbol) implements ComplexV { }
