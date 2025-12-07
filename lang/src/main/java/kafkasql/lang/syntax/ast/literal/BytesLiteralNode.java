package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;

public final record BytesLiteralNode(
    Range range,
    String text
) implements PrimitiveLiteralNode { }
