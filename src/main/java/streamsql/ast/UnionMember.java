package streamsql.ast;

public record UnionMember(Range range, Identifier name, AnyT typ) implements AstNode {}
