package kafkasql.core.ast;

public record ScalarT(Range range, QName qName, PrimitiveT primitive, AstOptionalNode<Expr> validation, AstOptionalNode<PrimitiveV> defaultValue) implements ComplexT {}
