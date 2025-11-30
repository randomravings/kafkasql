package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface Expr extends AstNode
    permits LiteralExpr, IdentifierExpr, PrefixExpr,
            InfixExpr, TrifixExpr, PostfixExpr,
            MemberExpr, IndexExpr, ParenExpr { }
