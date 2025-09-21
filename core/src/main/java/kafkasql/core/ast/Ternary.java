package kafkasql.core.ast;

import kafkasql.core.Range;

public final record Ternary(Range range, TernaryOp op, Expr left, Expr middle, Expr right, AnyT type) implements Expr { }
