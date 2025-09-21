package kafkasql.core.ast;

import kafkasql.core.Range;

public final record PostfixExpr(Range range, PostfixOp op, Expr expr, AnyT type) implements Expr { }
