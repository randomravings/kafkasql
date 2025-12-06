package kafkasql.lang;

import kafkasql.lang.syntax.ast.show.*;
import kafkasql.lang.syntax.ast.stmt.*;
import kafkasql.util.TestHelpers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SHOW and EXPLAIN statements
 */
class ShowExplainStatementsTest {

    @Test
    void showCurrentContext() {
        var stmts = TestHelpers.parseAssert("SHOW CURRENT CONTEXT;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowCurrentStmt.class, stmt);
        assertEquals(ShowTarget.CONTEXTS, stmt.target());
    }

    @Test
    void showAllContexts() {
        var stmts = TestHelpers.parseAssert("SHOW ALL CONTEXTS;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowAllStmt.class, stmt);
        assertEquals(ShowTarget.CONTEXTS, stmt.target());
    }

    @Test
    void showAllTypes() {
        var stmts = TestHelpers.parseAssert("SHOW ALL TYPES;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowAllStmt.class, stmt);
        assertEquals(ShowTarget.TYPES, stmt.target());
    }

    @Test
    void showAllStreams() {
        var stmts = TestHelpers.parseAssert("SHOW ALL STREAMS;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowAllStmt.class, stmt);
        assertEquals(ShowTarget.STREAMS, stmt.target());
    }

    @Test
    void showContextsWithoutQName() {
        var stmts = TestHelpers.parseAssert("SHOW CONTEXTS;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.CONTEXTS, scs.target());
        assertTrue(scs.qname().isEmpty());
    }

    @Test
    void showContextsWithQName() {
        var stmts = TestHelpers.parseAssert("SHOW CONTEXTS com.example;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.CONTEXTS, scs.target());
        assertTrue(scs.qname().isPresent());
        assertEquals("com.example", scs.qname().get().fullName());
    }

    @Test
    void showTypesWithoutQName() {
        var stmts = TestHelpers.parseAssert("SHOW TYPES;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.TYPES, scs.target());
        assertTrue(scs.qname().isEmpty());
    }

    @Test
    void showTypesWithQName() {
        var stmts = TestHelpers.parseAssert("SHOW TYPES com.example;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.TYPES, scs.target());
        assertTrue(scs.qname().isPresent());
        assertEquals("com.example", scs.qname().get().fullName());
    }

    @Test
    void showStreamsWithoutQName() {
        var stmts = TestHelpers.parseAssert("SHOW STREAMS;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.STREAMS, scs.target());
        assertTrue(scs.qname().isEmpty());
    }

    @Test
    void showStreamsWithQName() {
        var stmts = TestHelpers.parseAssert("SHOW STREAMS com.example;");
        ShowStmt stmt = TestHelpers.only(stmts, ShowStmt.class);
        assertInstanceOf(ShowContextualStmt.class, stmt);
        ShowContextualStmt scs = (ShowContextualStmt) stmt;
        assertEquals(ShowTarget.STREAMS, scs.target());
        assertTrue(scs.qname().isPresent());
        assertEquals("com.example", scs.qname().get().fullName());
    }

    @Test
    void explainStatement() {
        var stmts = TestHelpers.parseAssert("EXPLAIN com.example.MyType;");
        ExplainStmt stmt = TestHelpers.only(stmts, ExplainStmt.class);
        assertEquals("com.example.MyType", stmt.target().fullName());
    }

    @Test
    void mixedStatements() {
        var stmts = TestHelpers.parseAssert("""
            CREATE CONTEXT test;
            SHOW CONTEXTS;
            USE CONTEXT test;
            SHOW CURRENT CONTEXT;
            CREATE TYPE Foo AS SCALAR INT32;
            SHOW TYPES test;
            EXPLAIN test.Foo;
            """);
        
        assertEquals(7, stmts.size());
        assertInstanceOf(CreateStmt.class, stmts.get(0));
        assertInstanceOf(ShowStmt.class, stmts.get(1));
        assertInstanceOf(UseStmt.class, stmts.get(2));
        assertInstanceOf(ShowStmt.class, stmts.get(3));
        assertInstanceOf(CreateStmt.class, stmts.get(4));
        assertInstanceOf(ShowStmt.class, stmts.get(5));
        assertInstanceOf(ExplainStmt.class, stmts.get(6));
    }
}
