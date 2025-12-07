package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;

public record MapLiteralNode(
    Range range,
    AstListNode<MapEntryLiteralNode> entries
) implements CompositeLiteralNode { }
