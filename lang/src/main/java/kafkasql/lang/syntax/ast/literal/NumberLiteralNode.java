package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;

public final record NumberLiteralNode(
    Range range,
    String text
) implements PrimitiveLiteralNode { }
