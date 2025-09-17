package kafkasql.core.ast;

public record EnumSymbol(Range range, Identifier name, IntegerV value) implements AstNode {}
