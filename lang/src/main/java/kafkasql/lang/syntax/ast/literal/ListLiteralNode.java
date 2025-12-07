package kafkasql.lang.syntax.ast.literal;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;

public record ListLiteralNode(
    Range range,
    AstListNode<LiteralNode> elements
) implements CompositeLiteralNode { }
