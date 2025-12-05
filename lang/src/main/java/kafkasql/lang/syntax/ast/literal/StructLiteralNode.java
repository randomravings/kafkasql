package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;

public record StructLiteralNode(
    Range range,
    AstListNode<StructFieldLiteralNode> fields
) implements ComplexLiteralNode { }
