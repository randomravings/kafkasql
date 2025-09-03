package streamsql.ast;

public record Binary(BinOp op, Expr left, Expr right) implements Expr {}
