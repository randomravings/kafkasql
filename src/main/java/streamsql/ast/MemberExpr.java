package streamsql.ast;

public final record MemberExpr(Expr target, Identifier name, AnyT type) implements Expr { }
