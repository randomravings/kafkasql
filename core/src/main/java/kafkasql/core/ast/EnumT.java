package kafkasql.core.ast;

public record EnumT(Range range, QName qName, AstOptionalNode<IntegerT> type, EnumSymbolList symbols, AstOptionalNode<Identifier> defaultSymbol) implements ComplexT {}
