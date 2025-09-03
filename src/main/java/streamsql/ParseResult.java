package streamsql;

import java.util.List;

import streamsql.ast.Stmt;

public record ParseResult(
    List<Stmt> stmts,
    Diagnostics diags
) {}
