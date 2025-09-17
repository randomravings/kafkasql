package kafkasql.core;

import kafkasql.core.ast.Ast;

public record ParseResult(
    Ast ast,
    Diagnostics diags
) {}
