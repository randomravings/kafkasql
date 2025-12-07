package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;

public final record NullLiteralNode(
    Range range
) implements LiteralNode { }
