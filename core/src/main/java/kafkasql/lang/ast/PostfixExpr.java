package kafkasql.lang.ast;

public final record PostfixExpr(Range range, PostfixOp op, Expr expr, AnyT type) implements Expr { }
