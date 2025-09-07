package streamsql.ast;

public sealed interface Expr permits Binary, Unary, Literal {}
