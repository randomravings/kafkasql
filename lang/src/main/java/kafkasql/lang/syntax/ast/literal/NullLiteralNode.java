package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;

public final record NullLiteralNode(
    Range range
) implements LiteralNode { }
