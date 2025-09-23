package kafkasql.core.ast;

import kafkasql.core.Range;

public record ScalarT(Range range, QName qName, PrimitiveT primitive, AstOptionalNode<CheckClause> checkClause, AstOptionalNode<PrimitiveV> defaultValue, AstOptionalNode<Doc> doc) implements ComplexT {}
