package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;

public final record StringLiteralNode(
    Range range,
    String value
) implements PrimitiveLiteralNode { }
