package kafkasql.core.ast;

public sealed interface Expr extends AstNode
    permits PrefixExpr, InfixExpr, PostfixExpr,Ternary, AnyV, IdentifierExpr, MemberExpr, IndexExpr { }
