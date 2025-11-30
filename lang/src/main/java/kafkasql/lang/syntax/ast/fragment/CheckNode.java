package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.expr.Expr;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record CheckNode(
    Range range,
    Identifier name,
    Expr expr
) implements AstNode { }
