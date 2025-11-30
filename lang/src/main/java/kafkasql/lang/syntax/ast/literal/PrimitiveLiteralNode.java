package kafkasql.lang.syntax.ast.literal;

public sealed interface PrimitiveLiteralNode
    extends LiteralNode
    permits BoolLiteralNode,
            NumberLiteralNode,
            StringLiteralNode,
            BytesLiteralNode
{ }
