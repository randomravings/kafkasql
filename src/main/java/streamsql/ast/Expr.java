package streamsql.ast;

public sealed interface Expr permits Binary, Not, Literal.Str, Literal.Num, Literal.Bool, Literal.Null, Ident {}
