package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.AstOptionalNode;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record ProjectionExprNode(
    Range range,
    Expr expr,
    AstOptionalNode<Identifier> alias
) implements AstNode { }
