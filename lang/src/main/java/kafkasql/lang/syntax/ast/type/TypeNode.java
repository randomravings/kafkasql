package kafkasql.lang.syntax.ast.type;

import kafkasql.lang.syntax.ast.AstNode;

public sealed interface TypeNode
    extends AstNode
    permits PrimitiveTypeNode,
            CompositeTypeNode,
            ComplexTypeNode
{ }
