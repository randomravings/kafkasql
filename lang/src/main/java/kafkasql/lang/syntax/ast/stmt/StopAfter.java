package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;

/**
 * Sealed hierarchy for the optional STOP AFTER clause on a READ statement.
 *
 * <pre>
 *   STOP AFTER n RECORDS
 *   STOP AFTER n SECONDS
 *   STOP AFTER n SECONDS IDLE
 * </pre>
 */
public sealed interface StopAfter extends AstNode
    permits StopAfter.Records,
            StopAfter.Seconds,
            StopAfter.SecondsIdle
{
    /** STOP AFTER n RECORDS */
    record Records(Range range, long count) implements StopAfter {}

    /** STOP AFTER n SECONDS — wall-clock timeout */
    record Seconds(Range range, long seconds) implements StopAfter {}

    /** STOP AFTER n SECONDS IDLE — no records received for n seconds */
    record SecondsIdle(Range range, long seconds) implements StopAfter {}
}
