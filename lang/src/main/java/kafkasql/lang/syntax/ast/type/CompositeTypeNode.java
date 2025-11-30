package kafkasql.lang.syntax.ast.type;

public sealed interface CompositeTypeNode
    extends TypeNode
    permits ListTypeNode,
            MapTypeNode
{ }
