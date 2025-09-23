package kafkasql.core.ast;

import kafkasql.core.Range;

public record EnumT(Range range, QName qName, AstOptionalNode<IntegerT> type, EnumSymbolList symbols, AstOptionalNode<EnumV> defaultValue, AstOptionalNode<Doc> doc) implements ComplexT {}
