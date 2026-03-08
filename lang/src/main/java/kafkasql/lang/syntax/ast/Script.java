package kafkasql.lang.syntax.ast;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.misc.Include;
import kafkasql.lang.syntax.ast.misc.VersionPragma;
import kafkasql.lang.syntax.ast.stmt.Stmt;

import java.util.Optional;

public record Script(
    Range range,
    AstListNode<Include> includes,
    Optional<VersionPragma> version,
    AstListNode<Stmt> statements
) implements AstNode { }
