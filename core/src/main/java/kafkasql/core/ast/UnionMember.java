package kafkasql.core.ast;

public record UnionMember(Range range, Identifier name, AnyT typ) implements AstNode {}
