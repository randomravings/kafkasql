package kafkasql.core.ast;

public record ScalarT(Range range, QName qName, PrimitiveT primitive, AstOptionalNode<CheckClause> checkClause, AstOptionalNode<PrimitiveV> defaultValue, AstOptionalNode<Doc> doc) implements ComplexT {}
