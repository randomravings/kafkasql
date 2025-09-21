package kafkasql.core.ast;

import kafkasql.core.Range;

public record ScalarT(Range range, QName qName, PrimitiveT primitive, AstOptionalNode<Expr> validation, AstOptionalNode<PrimitiveV> defaultValue) implements ComplexT {}
