package kafkasql.lang.syntax.ast.stmt;

import kafkasql.runtime.diagnostics.Range;
import kafkasql.lang.syntax.ast.AstListNode;
import kafkasql.lang.syntax.ast.AstNode;
import kafkasql.lang.syntax.ast.misc.QName;
import java.util.Optional;

public sealed interface CursorStmt extends Stmt
    permits CursorStmt.CreateCursor,
            CursorStmt.AlterCursorAdd,
            CursorStmt.AlterCursorRemove,
            CursorStmt.AlterCursorResetStream,
            CursorStmt.AlterCursorSeekStream,
            CursorStmt.DropCursor {

    enum ResetPolicy {
        EARLIEST,
        LATEST
    }

    record StreamBinding(
        Range range,
        QName stream,
        Optional<ResetPolicy> resetPolicy
    ) implements AstNode {}

    record CreateCursor(
        Range range,
        String cursorName,
        AstListNode<StreamBinding> streams
    ) implements CursorStmt {}

    record AlterCursorAdd(
        Range range,
        String cursorName,
        QName stream,
        Optional<ResetPolicy> resetPolicy
    ) implements CursorStmt {}

    record AlterCursorRemove(
        Range range,
        String cursorName,
        QName stream
    ) implements CursorStmt {}

    record AlterCursorResetStream(
        Range range,
        String cursorName,
        QName stream,
        ResetPolicy resetPolicy
    ) implements CursorStmt {}

    record PartitionSeek(
        Range range,
        int partition,
        SeekTarget target
    ) implements AstNode {}

    sealed interface SeekTarget extends AstNode
        permits SeekTarget.Beginning,
                SeekTarget.End,
                SeekTarget.Offset,
                SeekTarget.Timestamp {
        record Beginning(Range range) implements SeekTarget {}
        record End(Range range) implements SeekTarget {}
        record Offset(Range range, long offset) implements SeekTarget {}
        record Timestamp(Range range, String timestamp) implements SeekTarget {}
    }

    record AlterCursorSeekStream(
        Range range,
        String cursorName,
        QName stream,
        AstListNode<PartitionSeek> seeks
    ) implements CursorStmt {}

    record DropCursor(
        Range range,
        String cursorName
    ) implements CursorStmt {}
}