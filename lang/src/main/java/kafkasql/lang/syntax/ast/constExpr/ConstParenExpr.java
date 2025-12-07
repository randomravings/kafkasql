package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.runtime.diagnostics.Range;

public final record ConstParenExpr(
    Range range,
    ConstExpr inner
) implements ConstExpr { }
