package streamsql.ast;

public record Binary(BinaryOp op, Expr left, Expr right) implements Expr {}
