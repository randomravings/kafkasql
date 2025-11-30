package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.misc.Identifier;

public record StructFieldLiteralNode(
    Range range,
    Identifier name,
    LiteralNode value
) implements AstNode { }
