package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.runtime.diagnostics.Range;

public final record ConstLiteralExpr(
    Range range,
    String text
) implements ConstExpr { }
