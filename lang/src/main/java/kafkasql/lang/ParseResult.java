package kafkasql.lang;

import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.syntax.ast.Script;

public final record ParseResult (
    TypedList<Script> scripts,
    Diagnostics diags
) { }
