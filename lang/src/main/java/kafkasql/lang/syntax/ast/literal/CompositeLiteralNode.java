package kafkasql.lang.syntax.ast.literal;

public sealed interface CompositeLiteralNode
    extends LiteralNode
    permits ListLiteralNode,
            MapLiteralNode
{ }
