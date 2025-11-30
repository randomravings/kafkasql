package kafkasql.lang.semantic.lazy;

import kafkasql.lang.syntax.ast.literal.ComplexLiteralNode;

public final record UnresolvedComplexLiteral<V extends ComplexLiteralNode>(
    V node
) { }
