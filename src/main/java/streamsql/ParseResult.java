package streamsql;

import streamsql.ast.Ast;

public record ParseResult(
    Ast ast,
    Diagnostics diags
) {}
