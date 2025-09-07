package streamsql.ast;

public final record Unary(UnaryOp op, Expr expr) implements Expr {}