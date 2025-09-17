package kafkasql.core.ast;

public final record Ternary(Range range, TernaryOp op, Expr left, Expr middle, Expr right, AnyT type) implements Expr { }
