package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public record ProjectionNode(
    Range range,
    TypedList<ProjectionExprNode> items
) implements AstNode { }
