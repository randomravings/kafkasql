package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstNode;

public record ProjectionNode(
    Range range,
    AstListNode<ProjectionExprNode> items
) implements AstNode { }
