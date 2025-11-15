package kafkasql.core.ast;

public final record PrefixExpr(Range range, PrefixOp op, Expr expr, AnyT type) implements Expr { }