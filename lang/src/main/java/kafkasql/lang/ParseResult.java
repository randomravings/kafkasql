package kafkasql.lang;

import kafkasql.lang.diagnostics.Diagnostics;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.Script;

public final record ParseResult (
    AstListNode<Script> scripts,
    Diagnostics diags
) { }
