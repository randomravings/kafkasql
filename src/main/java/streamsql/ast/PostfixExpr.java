package streamsql.ast;

public final record PostfixExpr(PostfixOp op, Expr expr, AnyT type) implements Expr { }
