package kafkasql.core.ast;

import kafkasql.core.Range;

public final record IdentifierExpr(Range range, Identifier name, AnyT type) implements Expr { }
