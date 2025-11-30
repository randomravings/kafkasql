package kafkasql.lang.syntax.ast.literal;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface LiteralNode extends AstNode
    permits NullLiteralNode,
            PrimitiveLiteralNode,
            CompositeLiteralNode,
            ComplexLiteralNode
{ }
