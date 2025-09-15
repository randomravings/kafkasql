package streamsql.ast;

public final record IndexExpr(Expr target, Expr index, AnyT type) implements Expr { }
