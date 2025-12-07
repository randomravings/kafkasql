package kafkasql.lang.syntax.ast.fragment;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record ConstraintNode (
    Range range,
    Identifier name,
    DeclFragment fragment
) implements DeclFragment { }
