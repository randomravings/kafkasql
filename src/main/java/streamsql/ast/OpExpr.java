package streamsql.ast;

public sealed interface OpExpr extends Expr
    permits Unary, Binary, Ternary { }
