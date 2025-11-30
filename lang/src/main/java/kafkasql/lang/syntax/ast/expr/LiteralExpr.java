package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.literal.LiteralNode;

public record LiteralExpr(
    Range range,
    LiteralNode literal
) implements Expr { }
