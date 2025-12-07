package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;

public final record BoolLiteralNode(
    Range range,
    Boolean value
) implements PrimitiveLiteralNode { }
