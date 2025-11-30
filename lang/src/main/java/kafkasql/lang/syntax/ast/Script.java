package kafkasql.lang.syntax.ast;

import kafkasql.lang.TypedList;
import kafkasql.lang.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.stmt.Stmt;

public record Script(
    Range range,
    TypedList<Include> includes,
    TypedList<Stmt> statements
) implements AstNode { }
