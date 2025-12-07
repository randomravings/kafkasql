package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;

public final record NumberLiteralNode(
    Range range,
    String text
) implements PrimitiveLiteralNode { }
