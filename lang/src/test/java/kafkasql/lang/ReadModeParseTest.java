package kafkasql.lang;

import kafkasql.lang.syntax.ast.stmt.ReadMode;
import kafkasql.lang.syntax.ast.stmt.ReadMode.OffsetPosition;
import kafkasql.lang.syntax.ast.stmt.ReadStmt;
import kafkasql.util.TestHelpers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("READ mode clause – parser")
class ReadModeParseTest {

    // Preamble needed to satisfy stream reference (parse-only, no semantic binding)
    private static final String PREAMBLE = """
        CREATE CONTEXT orders;
        USE CONTEXT orders;
        CREATE TYPE OrderRecord AS STRUCT (Id INT32);
        CREATE STREAM Orders (TYPE OrderRecord AS orders.OrderRecord);
        """;

    private ReadStmt parseRead(String mode) {
        String text = PREAMBLE + "\nREAD FROM orders.Orders\n" + mode + "\nTYPE OrderRecord *;";
        var stmts = TestHelpers.parseAssert(text);
        var readStmt = stmts.stream()
            .filter(s -> s instanceof ReadStmt)
            .map(s -> (ReadStmt) s)
            .findFirst();
        assertTrue(readStmt.isPresent(), "Expected a ReadStmt");
        return readStmt.get();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // No mode clause — backward-compatibility
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("omitted mode clause → empty optional")
    void noModeClause() {
        String text = PREAMBLE + "\nREAD FROM orders.Orders TYPE OrderRecord *;";
        var stmts = TestHelpers.parseAssert(text);
        var read = stmts.stream()
            .filter(s -> s instanceof ReadStmt).map(s -> (ReadStmt) s)
            .findFirst().orElseThrow();
        assertTrue(read.mode().isEmpty(), "mode should be absent when clause is omitted");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FROM GROUP
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FROM GROUP")
    class FromGroupTests {

        @Test
        @DisplayName("no reset → group id captured, reset empty")
        void fromGroupNoReset() {
            ReadStmt read = parseRead("FROM GROUP 'my-consumer'");
            assertTrue(read.mode().isPresent());
            var c = assertInstanceOf(ReadMode.FromGroup.class, read.mode().get());
            assertEquals("my-consumer", c.groupId());
            assertTrue(c.resetToBeginning().isEmpty());
        }

        @Test
        @DisplayName("BEGINNING reset → resetToBeginning = true")
        void fromGroupBeginning() {
            ReadStmt read = parseRead("FROM GROUP 'replay-group' BEGINNING");
            var c = assertInstanceOf(ReadMode.FromGroup.class, read.mode().get());
            assertEquals("replay-group", c.groupId());
            assertEquals(Optional.of(true), c.resetToBeginning());
        }

        @Test
        @DisplayName("END reset → resetToBeginning = false")
        void fromGroupEnd() {
            ReadStmt read = parseRead("FROM GROUP 'live-group' END");
            var c = assertInstanceOf(ReadMode.FromGroup.class, read.mode().get());
            assertEquals("live-group", c.groupId());
            assertEquals(Optional.of(false), c.resetToBeginning());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FROM BEGINNING / FROM END
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FROM bound")
    class FromBoundTests {

        @Test
        @DisplayName("FROM BEGINNING → FromBeginning")
        void fromBeginning() {
            ReadStmt read = parseRead("FROM BEGINNING");
            assertInstanceOf(ReadMode.FromBeginning.class, read.mode().get());
        }

        @Test
        @DisplayName("FROM END → FromEnd")
        void fromEnd() {
            ReadStmt read = parseRead("FROM END");
            assertInstanceOf(ReadMode.FromEnd.class, read.mode().get());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FROM 'timestamp'  (was AS OF)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FROM timestamp")
    class FromTimestampTests {

        @Test
        @DisplayName("FROM 'timestamp' captures the string")
        void fromTimestamp() {
            ReadStmt read = parseRead("FROM '2026-01-15T12:00:00Z'");
            var c = assertInstanceOf(ReadMode.FromTimestamp.class, read.mode().get());
            assertEquals("2026-01-15T12:00:00Z", c.timestamp());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FROM OFFSETS
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FROM OFFSETS")
    class FromOffsetsTests {

        @Test
        @DisplayName("single partition, BEGINNING")
        void singleBeginning() {
            ReadStmt read = parseRead("FROM OFFSETS (0: BEGINNING)");
            var c = assertInstanceOf(ReadMode.FromOffsets.class, read.mode().get());
            assertEquals(1, c.specs().size());
            assertEquals(0, c.specs().get(0).partition());
            assertInstanceOf(OffsetPosition.Beginning.class, c.specs().get(0).position());
        }

        @Test
        @DisplayName("single partition, END")
        void singleEnd() {
            ReadStmt read = parseRead("FROM OFFSETS (1: END)");
            var c = assertInstanceOf(ReadMode.FromOffsets.class, read.mode().get());
            assertEquals(1, c.specs().get(0).partition());
            assertInstanceOf(OffsetPosition.End.class, c.specs().get(0).position());
        }

        @Test
        @DisplayName("single partition, numeric offset")
        void singleOffset() {
            ReadStmt read = parseRead("FROM OFFSETS (2: 42)");
            var c = assertInstanceOf(ReadMode.FromOffsets.class, read.mode().get());
            assertEquals(2, c.specs().get(0).partition());
            var pos = assertInstanceOf(OffsetPosition.Offset.class, c.specs().get(0).position());
            assertEquals(42L, pos.offset());
        }

        @Test
        @DisplayName("multiple partitions with mixed positions")
        void multipleOffsets() {
            ReadStmt read = parseRead("""
                FROM OFFSETS (
                    0: BEGINNING,
                    1: END,
                    2: 99
                )""");
            var c = assertInstanceOf(ReadMode.FromOffsets.class, read.mode().get());
            assertEquals(3, c.specs().size());

            assertEquals(0, c.specs().get(0).partition());
            assertInstanceOf(OffsetPosition.Beginning.class, c.specs().get(0).position());

            assertEquals(1, c.specs().get(1).partition());
            assertInstanceOf(OffsetPosition.End.class, c.specs().get(1).position());

            assertEquals(2, c.specs().get(2).partition());
            assertEquals(99L, ((OffsetPosition.Offset) c.specs().get(2).position()).offset());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FROM TIMESTAMPS
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FROM TIMESTAMPS")
    class FromTimestampsTests {

        @Test
        @DisplayName("single partition timestamp")
        void singleTimestamp() {
            ReadStmt read = parseRead("FROM TIMESTAMPS (0: '2026-01-15T12:00:00Z')");
            var c = assertInstanceOf(ReadMode.FromTimestamps.class, read.mode().get());
            assertEquals(1, c.specs().size());
            assertEquals(0, c.specs().get(0).partition());
            assertEquals("2026-01-15T12:00:00Z", c.specs().get(0).timestamp());
        }

        @Test
        @DisplayName("multiple partition timestamps")
        void multipleTimestamps() {
            ReadStmt read = parseRead("""
                FROM TIMESTAMPS (
                    0: '2026-01-15T12:00:00Z',
                    1: '2026-06-01T00:00:00Z'
                )""");
            var c = assertInstanceOf(ReadMode.FromTimestamps.class, read.mode().get());
            assertEquals(2, c.specs().size());
            assertEquals(0, c.specs().get(0).partition());
            assertEquals("2026-01-15T12:00:00Z", c.specs().get(0).timestamp());
            assertEquals(1, c.specs().get(1).partition());
            assertEquals("2026-06-01T00:00:00Z", c.specs().get(1).timestamp());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Syntax errors
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Syntax errors")
    class SyntaxErrorTests {

        @Test
        @DisplayName("FROM GROUP without string literal → parse error")
        void fromGroupMissingName() {
            String text = PREAMBLE + "\nREAD FROM orders.Orders FROM GROUP TYPE OrderRecord *;";
            var result = TestHelpers.parse(text);
            assertTrue(result.diags().hasError(), "Missing group name should produce a parse error");
        }

        @Test
        @DisplayName("FROM OFFSETS empty list → parse error")
        void fromOffsetsEmpty() {
            String text = PREAMBLE + "\nREAD FROM orders.Orders FROM OFFSETS () TYPE OrderRecord *;";
            var result = TestHelpers.parse(text);
            assertTrue(result.diags().hasError(), "Empty offset list should produce a parse error");
        }

        @Test
        @DisplayName("FROM TIMESTAMPS empty list → parse error")
        void fromTimestampsEmpty() {
            String text = PREAMBLE + "\nREAD FROM orders.Orders FROM TIMESTAMPS () TYPE OrderRecord *;";
            var result = TestHelpers.parse(text);
            assertTrue(result.diags().hasError(), "Empty timestamp list should produce a parse error");
        }
    }
}

