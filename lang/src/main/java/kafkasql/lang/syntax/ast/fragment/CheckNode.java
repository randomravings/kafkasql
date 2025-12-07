package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.expr.Expr;

public final record CheckNode(
    Range range,
    Expr expr
) implements DeclFragment { }
