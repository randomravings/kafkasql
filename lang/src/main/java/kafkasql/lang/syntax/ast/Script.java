package kafkasql.lang.syntax.ast;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.stmt.Stmt;

public record Script(
    Range range,
    AstListNode<Include> includes,
    AstListNode<Stmt> statements
) implements AstNode { }
