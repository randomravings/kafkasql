package kafkasql.lang;

import kafkasql.lang.syntax.ast.stmt.ReadStmt;
import kafkasql.lang.syntax.ast.stmt.StopAfter;
import kafkasql.util.TestHelpers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("STOP AFTER clause – parser")
class StopAfterParseTest {

    private static final String PREAMBLE = """
        CREATE CONTEXT orders;
        USE CONTEXT orders;
        CREATE TYPE OrderRecord AS STRUCT (Id INT32);
        CREATE STREAM Orders (TYPE OrderRecord AS orders.OrderRecord);
        """;

    /** Parses a READ statement with the given mode + stop clauses (both optional) and returns the ReadStmt. */
    private ReadStmt parseRead(String clauses) {
        String text = PREAMBLE + "\nREAD FROM orders.Orders\n" + clauses + "\nTYPE OrderRecord *;";
        var stmts = TestHelpers.parseAssert(text);
        return stmts.stream()
            .filter(s -> s instanceof ReadStmt)
            .map(s -> (ReadStmt) s)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a ReadStmt"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // No STOP AFTER clause — backward-compatibility
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("omitted STOP AFTER → empty optional")
    void noStopAfterClause() {
        ReadStmt read = parseRead("");
        assertTrue(read.stopAfter().isEmpty(), "stopAfter should be absent when clause is omitted");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STOP AFTER n RECORDS
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("STOP AFTER n RECORDS")
    class RecordsTests {

        @Test
        @DisplayName("count is captured")
        void stopAfterRecords() {
            ReadStmt read = parseRead("STOP AFTER 100 RECORDS");
            var sa = assertInstanceOf(StopAfter.Records.class, read.stopAfter().get());
            assertEquals(100L, sa.count());
        }

        @Test
        @DisplayName("count = 1 edge case")
        void stopAfterOneRecord() {
            ReadStmt read = parseRead("STOP AFTER 1 RECORDS");
            var sa = assertInstanceOf(StopAfter.Records.class, read.stopAfter().get());
            assertEquals(1L, sa.count());
        }

        @Test
        @DisplayName("large count")
        void stopAfterLargeCount() {
            ReadStmt read = parseRead("STOP AFTER 1000000 RECORDS");
            var sa = assertInstanceOf(StopAfter.Records.class, read.stopAfter().get());
            assertEquals(1_000_000L, sa.count());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STOP AFTER n SECONDS
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("STOP AFTER n SECONDS")
    class SecondsTests {

        @Test
        @DisplayName("seconds count is captured")
        void stopAfterSeconds() {
            ReadStmt read = parseRead("STOP AFTER 30 SECONDS");
            var sa = assertInstanceOf(StopAfter.Seconds.class, read.stopAfter().get());
            assertEquals(30L, sa.seconds());
        }

        @Test
        @DisplayName("seconds = 1 edge case")
        void stopAfterOneSecond() {
            ReadStmt read = parseRead("STOP AFTER 1 SECONDS");
            var sa = assertInstanceOf(StopAfter.Seconds.class, read.stopAfter().get());
            assertEquals(1L, sa.seconds());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STOP AFTER n SECONDS IDLE
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("STOP AFTER n SECONDS IDLE")
    class SecondsIdleTests {

        @Test
        @DisplayName("idle seconds count is captured")
        void stopAfterSecondsIdle() {
            ReadStmt read = parseRead("STOP AFTER 5 SECONDS IDLE");
            var sa = assertInstanceOf(StopAfter.SecondsIdle.class, read.stopAfter().get());
            assertEquals(5L, sa.seconds());
        }

        @Test
        @DisplayName("SECONDS IDLE preferred over SECONDS when IDLE follows")
        void secondsIdleNotSeconds() {
            ReadStmt read = parseRead("STOP AFTER 10 SECONDS IDLE");
            // Must be SecondsIdle, not Seconds
            assertInstanceOf(StopAfter.SecondsIdle.class, read.stopAfter().get());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STOP AFTER combined with a FROM mode clause
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("STOP AFTER combined with FROM mode")
    class CombinedTests {

        @Test
        @DisplayName("FROM BEGINNING + STOP AFTER RECORDS")
        void fromBeginningWithRecords() {
            ReadStmt read = parseRead("FROM BEGINNING\nSTOP AFTER 50 RECORDS");
            assertTrue(read.mode().isPresent(), "mode should be present");
            var sa = assertInstanceOf(StopAfter.Records.class, read.stopAfter().get());
            assertEquals(50L, sa.count());
        }

        @Test
        @DisplayName("FROM CURSOR + STOP AFTER SECONDS IDLE")
        void fromCursorWithSecondsIdle() {
            ReadStmt read = parseRead("FROM CURSOR 'my-group'\nSTOP AFTER 10 SECONDS IDLE");
            assertTrue(read.mode().isPresent(), "mode should be present");
            var sa = assertInstanceOf(StopAfter.SecondsIdle.class, read.stopAfter().get());
            assertEquals(10L, sa.seconds());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Syntax errors
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Syntax errors")
    class SyntaxErrorTests {

        @Test
        @DisplayName("STOP AFTER without number → parse error")
        void stopAfterMissingNumber() {
            String text = PREAMBLE + "\nREAD FROM orders.Orders STOP AFTER RECORDS TYPE OrderRecord *;";
            var result = TestHelpers.parse(text);
            assertTrue(result.diags().hasError(), "Missing number should produce a parse error");
        }

        @Test
        @DisplayName("STOP AFTER without unit → parse error")
        void stopAfterMissingUnit() {
            String text = PREAMBLE + "\nREAD FROM orders.Orders STOP AFTER 10 TYPE OrderRecord *;";
            var result = TestHelpers.parse(text);
            assertTrue(result.diags().hasError(), "Missing unit (RECORDS/SECONDS) should produce a parse error");
        }
    }
}
