package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.show.ShowTarget;

/**
 * SHOW CURRENT CONTEXT;
 */
public record ShowCurrentStmt(Range range) implements ShowStmt {
    @Override
    public ShowTarget target() {
        return ShowTarget.CONTEXTS;
    }
}
