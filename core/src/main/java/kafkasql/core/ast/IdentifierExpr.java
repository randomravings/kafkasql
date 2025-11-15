package kafkasql.core.ast;

public final record IdentifierExpr(Range range, Identifier name, AnyT type) implements Expr { }
