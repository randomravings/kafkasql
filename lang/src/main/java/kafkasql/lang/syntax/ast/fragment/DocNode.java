package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

public final record DocNode(
    Range range,
    String comment
) implements AstNode { }
