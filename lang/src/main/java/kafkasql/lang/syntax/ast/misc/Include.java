package kafkasql.lang.syntax.ast.misc;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public final record Include(
    Range range,
    String path
) implements AstNode { }
