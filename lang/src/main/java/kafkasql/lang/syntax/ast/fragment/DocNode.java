package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;

public final record DocNode(
    Range range,
    String comment
) implements DeclFragment { }
