package kafkasql.lang.syntax.ast.type;

import kafkasql.lang.diagnostics.Range;

public final record ListTypeNode(
    Range range,
    TypeNode elementType
) implements CompositeTypeNode { }
