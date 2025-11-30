package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public record MapEntryLiteralNode(
    Range range,
    PrimitiveLiteralNode key,
    LiteralNode value
) implements AstNode { }
