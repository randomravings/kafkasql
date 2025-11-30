package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;

public final record BytesLiteralNode(
    Range range,
    String text
) implements PrimitiveLiteralNode { }
