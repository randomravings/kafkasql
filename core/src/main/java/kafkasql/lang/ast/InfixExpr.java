package kafkasql.lang.ast;

public record InfixExpr(Range range, InfixOp op, Expr left, Expr right, AnyT type) implements Expr {}
