package kafkasql.lang.syntax.ast.expr;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record IdentifierExpr(
    Range range,
    Identifier name
) implements Expr { }
