package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.diagnostics.Range;

public final record DocNode(
    Range range,
    String comment
) implements DeclFragment { }
