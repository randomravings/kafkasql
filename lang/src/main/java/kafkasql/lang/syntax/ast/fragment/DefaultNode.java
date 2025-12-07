package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.literal.LiteralNode;

public final record DefaultNode (
    Range range,
    LiteralNode value
) implements DeclFragment { }
