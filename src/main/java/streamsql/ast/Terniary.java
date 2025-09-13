package streamsql.ast;

public final record Terniary(TerniaryOp op, Expr left, Expr middle, Expr right) implements OpExpr { }
