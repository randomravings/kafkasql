package streamsql.ast;

public sealed interface Expr
    permits PrefixExpr, InfixExpr, PostfixExpr,Ternary, AnyV, Symbol, MemberExpr, IndexExpr { }
