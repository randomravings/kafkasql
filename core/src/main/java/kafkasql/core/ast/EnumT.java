package kafkasql.core.ast;

public record EnumT(Range range, QName qName, AstOptionalNode<IntegerT> type, EnumSymbolList symbols, AstOptionalNode<EnumV> defaultValue, AstOptionalNode<Doc> doc) implements ComplexT {}
