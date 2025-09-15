package streamsql.ast;

public final record PrefixExpr(PrefixOp op, Expr expr, AnyT type) implements Expr { }