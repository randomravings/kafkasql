package kafkasql.core;

import kafkasql.core.ast.Ast;

public final record ParseResult (Ast ast, Diagnostics diags) { }
