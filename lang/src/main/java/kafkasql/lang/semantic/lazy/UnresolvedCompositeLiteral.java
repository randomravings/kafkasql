package kafkasql.lang.semantic.lazy;

import kafkasql.lang.syntax.ast.literal.CompositeLiteralNode;

public final record UnresolvedCompositeLiteral<V extends CompositeLiteralNode>(
    V node
) { }
