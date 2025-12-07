package kafkasql.lang.syntax.ast.type;

import kafkasql.runtime.diagnostics.Range;

public final record ListTypeNode(
    Range range,
    TypeNode elementType
) implements CompositeTypeNode { }
