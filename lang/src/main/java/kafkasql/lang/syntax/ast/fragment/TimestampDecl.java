package kafkasql.lang.syntax.ast.fragment;

import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Identifier;

public final record TimestampDecl(
    Range range,
    Identifier field
) implements DeclFragment {
    
}
