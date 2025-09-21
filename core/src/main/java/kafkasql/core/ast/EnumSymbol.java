package kafkasql.core.ast;

import kafkasql.core.Range;

public record EnumSymbol(Range range, Identifier name, IntegerV value) implements AstNode {}
