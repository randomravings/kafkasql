package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.lang.diagnostics.Range;

public final record ConstBinaryExpr(
    Range range,
    ConstExpr left,
    ConstExpr right,
    ConstBinaryOp op
) implements ConstExpr { }
