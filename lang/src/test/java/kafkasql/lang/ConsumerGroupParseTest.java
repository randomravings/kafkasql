package kafkasql.lang;

import kafkasql.lang.syntax.ast.stmt.CursorStmt;
import kafkasql.util.TestHelpers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cursor statements - parser")
class CursorParseTest {

    @Test
    @DisplayName("CREATE CURSOR FOR STREAMS with per-stream reset policies")
    void createCursorWithStreamBindings() {
        var stmts = TestHelpers.parseAssert("""
            CREATE CURSOR 'orders-cg' FOR STREAMS (
              orders.Orders RESET EARLIEST,
              customers.Customers
            );
            """);

        assertEquals(1, stmts.size());
        var stmt = assertInstanceOf(CursorStmt.CreateCursor.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
        assertEquals(2, stmt.streams().size());
        assertEquals("orders.Orders", stmt.streams().get(0).stream().fullName());
        assertEquals(CursorStmt.ResetPolicy.EARLIEST, stmt.streams().get(0).resetPolicy().orElseThrow());
        assertEquals("customers.Customers", stmt.streams().get(1).stream().fullName());
        assertTrue(stmt.streams().get(1).resetPolicy().isEmpty());
    }

    @Test
    @DisplayName("CREATE CURSOR FOR STREAMS with one stream")
    void createCursorSingleStream() {
        var stmts = TestHelpers.parseAssert("""
            CREATE CURSOR 'replay-cg' FOR STREAMS (orders.Orders RESET EARLIEST);
            """);

        var stmt = assertInstanceOf(CursorStmt.CreateCursor.class, stmts.get(0));
        assertEquals(1, stmt.streams().size());
        assertEquals("orders.Orders", stmt.streams().get(0).stream().fullName());
        assertEquals(CursorStmt.ResetPolicy.EARLIEST, stmt.streams().get(0).resetPolicy().orElseThrow());
    }

    @Test
    @DisplayName("ALTER CURSOR ADD STREAM with RESET LATEST")
    void alterCursorAddWithLatestReset() {
        var stmts = TestHelpers.parseAssert("""
            ALTER CURSOR 'orders-cg' ADD STREAM orders.Refunds RESET LATEST;
            """);

        var stmt = assertInstanceOf(CursorStmt.AlterCursorAdd.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
        assertEquals("orders.Refunds", stmt.stream().fullName());
        assertEquals(CursorStmt.ResetPolicy.LATEST, stmt.resetPolicy().orElseThrow());
    }

    @Test
    @DisplayName("ALTER CURSOR REMOVE STREAM")
    void alterCursorRemove() {
        var stmts = TestHelpers.parseAssert("""
            ALTER CURSOR 'orders-cg' REMOVE STREAM orders.Orders;
            """);

        var stmt = assertInstanceOf(CursorStmt.AlterCursorRemove.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
        assertEquals("orders.Orders", stmt.stream().fullName());
    }

    @Test
    @DisplayName("ALTER CURSOR RESET STREAM TO BEGINNING")
    void alterCursorResetStreamToBeginning() {
        var stmts = TestHelpers.parseAssert("""
            ALTER CURSOR 'orders-cg' RESET STREAM orders.Orders TO BEGINNING;
            """);

        var stmt = assertInstanceOf(CursorStmt.AlterCursorResetStream.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
        assertEquals("orders.Orders", stmt.stream().fullName());
        assertEquals(CursorStmt.ResetPolicy.EARLIEST, stmt.resetPolicy());
    }

    @Test
    @DisplayName("ALTER CURSOR SEEK STREAM TO mixed targets")
    void alterCursorSeekStreamWithMixedTargets() {
        var stmts = TestHelpers.parseAssert("""
            ALTER CURSOR 'orders-cg' SEEK STREAM orders.Orders TO (
              0: BEGINNING,
              1: END,
              3: 1001,
              4: '2026-07-01T00:00:00.001Z'
            );
            """);

        var stmt = assertInstanceOf(CursorStmt.AlterCursorSeekStream.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
        assertEquals("orders.Orders", stmt.stream().fullName());
        assertEquals(4, stmt.seeks().size());
        assertEquals(0, stmt.seeks().get(0).partition());
        assertInstanceOf(CursorStmt.SeekTarget.Beginning.class, stmt.seeks().get(0).target());
        assertEquals(1, stmt.seeks().get(1).partition());
        assertInstanceOf(CursorStmt.SeekTarget.End.class, stmt.seeks().get(1).target());
        assertEquals(1001L, assertInstanceOf(CursorStmt.SeekTarget.Offset.class, stmt.seeks().get(2).target()).offset());
        assertEquals("2026-07-01T00:00:00.001Z",
            assertInstanceOf(CursorStmt.SeekTarget.Timestamp.class, stmt.seeks().get(3).target()).timestamp());
    }

    @Test
    @DisplayName("DROP CURSOR")
    void dropCursor() {
        var stmts = TestHelpers.parseAssert("""
            DROP CURSOR 'orders-cg';
            """);

        var stmt = assertInstanceOf(CursorStmt.DropCursor.class, stmts.get(0));
        assertEquals("orders-cg", stmt.cursorName());
    }
}
