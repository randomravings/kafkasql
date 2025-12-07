package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.show.ShowTarget;
import kafkasql.lang.syntax.ast.misc.QName;

import java.util.Optional;

/**
 * SHOW CONTEXTS|TYPES|STREAMS [qname];
 * If qname is empty, uses current context.
 */
public record ShowContextualStmt(
    Range range, 
    ShowTarget target,
    Optional<QName> qname
) implements ShowStmt { }
