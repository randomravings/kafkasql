package kafkasql.lang;

import kafkasql.lang.ast.Ast;

public final record ParseResult (Ast ast, Diagnostics diags) { }
