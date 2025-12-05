package kafkasql.lang.syntax.ast.constExpr;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record ConstSymbolRefExpr(
    Range range,
    Identifier name
) implements ConstExpr { }
