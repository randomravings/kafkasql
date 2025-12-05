package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.lang.diagnostics.Range;

public final record ConstLiteralExpr(
    Range range,
    String text
) implements ConstExpr { }
