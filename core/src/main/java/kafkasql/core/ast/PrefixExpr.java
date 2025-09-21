package kafkasql.core.ast;

import kafkasql.core.Range;

public final record PrefixExpr(Range range, PrefixOp op, Expr expr, AnyT type) implements Expr { }