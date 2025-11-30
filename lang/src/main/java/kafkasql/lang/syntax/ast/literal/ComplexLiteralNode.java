package kafkasql.lang.syntax.ast.literal;

public sealed interface ComplexLiteralNode
    extends LiteralNode
    permits StructLiteralNode,
            EnumLiteralNode,
            UnionLiteralNode
{ }
