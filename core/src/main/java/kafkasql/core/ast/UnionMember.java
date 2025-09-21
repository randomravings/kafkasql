package kafkasql.core.ast;

import kafkasql.core.Range;

public record UnionMember(Range range, Identifier name, AnyT typ) implements AstNode {}
