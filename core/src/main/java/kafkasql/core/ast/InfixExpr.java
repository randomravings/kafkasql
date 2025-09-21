package kafkasql.core.ast;

import kafkasql.core.Range;

public record InfixExpr(Range range, InfixOp op, Expr left, Expr right, AnyT type) implements Expr {}
