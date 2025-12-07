package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.show.ShowTarget;

/**
 * SHOW ALL CONTEXTS|TYPES|STREAMS;
 */
public record ShowAllStmt(Range range, ShowTarget target) implements ShowStmt { }
