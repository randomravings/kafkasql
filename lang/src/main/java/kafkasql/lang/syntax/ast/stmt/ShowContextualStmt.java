package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.show.ShowTarget;

import java.util.Optional;

/**
 * SHOW CONTEXTS|TYPES|STREAMS|USERS ['pattern'];
 * If filter is empty, returns all items. The pattern may contain '*' wildcards.
 */
public record ShowContextualStmt(
    Range range,
    ShowTarget target,
    Optional<String> filter
) implements ShowStmt { }
