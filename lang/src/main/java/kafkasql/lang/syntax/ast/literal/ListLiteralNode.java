package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;

public record ListLiteralNode(
    Range range,
    TypedList<LiteralNode> elements
) implements CompositeLiteralNode { }
