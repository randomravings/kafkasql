package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface ConstExpr
    extends AstNode
    permits ConstLiteralExpr,
            ConstSymbolRefExpr,
            ConstBinaryExpr,
            ConstParenExpr
{ }
