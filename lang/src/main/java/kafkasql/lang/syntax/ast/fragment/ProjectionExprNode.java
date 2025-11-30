package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.TypedOptional;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record ProjectionExprNode(
    Range range,
    Expr expr,
    TypedOptional<Identifier> alias
) implements AstNode { }
