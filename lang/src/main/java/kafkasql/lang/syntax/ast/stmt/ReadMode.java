package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.AstListNode;
import java.util.Optional;

/**
 * Sealed hierarchy for the optional read mode clause on a READ statement.
 *
 * <pre>
 *   FROM GROUP 'name' [BEGINNING|END]
 *   FROM BEGINNING
 *   FROM END
 *   FROM 'iso-timestamp'
 *   FROM OFFSETS   (0: BEGINNING|END|number, ...)
 *   FROM TIMESTAMPS (0: 'timestamp', ...)
 * </pre>
 */
public sealed interface ReadMode extends AstNode
    permits ReadMode.FromGroup,
            ReadMode.FromBeginning,
            ReadMode.FromEnd,
            ReadMode.FromTimestamp,
            ReadMode.FromOffsets,
            ReadMode.FromTimestamps
{
    /** FROM GROUP 'name' [BEGINNING|END] */
    record FromGroup(
        Range range,
        String groupId,
        Optional<Boolean> resetToBeginning  // true=BEGINNING, false=END, empty=default(END)
    ) implements ReadMode {}

    /** FROM BEGINNING */
    record FromBeginning(Range range) implements ReadMode {}

    /** FROM END */
    record FromEnd(Range range) implements ReadMode {}

    /** FROM 'iso-timestamp' — seek all partitions to near that timestamp */
    record FromTimestamp(
        Range range,
        String timestamp
    ) implements ReadMode {}

    /** FROM OFFSETS (idx: BEGINNING|END|number, ...) */
    record FromOffsets(
        Range range,
        AstListNode<OffsetSpec> specs
    ) implements ReadMode {}

    /** FROM TIMESTAMPS (idx: 'timestamp', ...) */
    record FromTimestamps(
        Range range,
        AstListNode<TimestampSpec> specs
    ) implements ReadMode {}

    // ── offset spec ───────────────────────────────────────────────────────────

    record OffsetSpec(
        Range range,
        int partition,
        OffsetPosition position
    ) implements AstNode {}

    sealed interface OffsetPosition extends AstNode
        permits OffsetPosition.Beginning,
                OffsetPosition.End,
                OffsetPosition.Offset
    {
        record Beginning(Range range)           implements OffsetPosition {}
        record End(Range range)                 implements OffsetPosition {}
        record Offset(Range range, long offset) implements OffsetPosition {}
    }

    // ── timestamp spec ────────────────────────────────────────────────────────

    record TimestampSpec(
        Range range,
        int partition,
        String timestamp
    ) implements AstNode {}
}
