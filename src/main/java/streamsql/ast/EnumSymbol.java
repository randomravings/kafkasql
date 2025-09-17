package streamsql.ast;

public record EnumSymbol(Range range, Identifier name, IntegerV value) implements AstNode {}
