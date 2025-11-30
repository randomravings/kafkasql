package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;

public record MapLiteralNode(
    Range range,
    TypedList<MapEntryLiteralNode> entries
) implements CompositeLiteralNode { }
