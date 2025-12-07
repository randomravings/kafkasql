package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.expr.Expr;

public record WhereNode(
    Range range,
    Expr expr
) implements AstNode { }
