package streamsql.ast;

public record InfixExpr(InfixOp op, Expr left, Expr right, AnyT type) implements Expr {}
