package streamsql.ast;

public final record Ternary(TernaryOp op, Expr left, Expr middle, Expr right) implements OpExpr { }
