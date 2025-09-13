package streamsql.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import streamsql.ParseArgs;
import streamsql.ParseHelpers;
import streamsql.ParseResult;
import streamsql.ast.Stmt;

public class TestHelpers {
    private TestHelpers() {
    }

    public static ParseResult parse(String script) {
        return ParseHelpers.parse(new ParseArgs(false, false), script);
    }

    public static List<Stmt> parseAssert(String script) {
        var result = ParseHelpers.parse(new ParseArgs(false, false), script);
        assertNoErrors(result);
        return result.stmts();
    }

    public static void assertNoErrors(ParseResult result) {
        if (result.diags().hasFatal()) {
            System.out.println(result.diags().fatal());
            fail("Expected no fatal errors, but found some");
        }
        if (result.diags().hasErrors()) {
            result.diags().errors().forEach(System.out::println);
            fail("Expected no errors, but found some");
        }
    }

    public static <T> T assertType(Object stmt, Class<T> clazz) {
        assertNotNull(stmt, "Statement is null");
        assertTrue(clazz.isInstance(stmt),
                "Expected " + clazz.getSimpleName() + " but got " + stmt.getClass().getSimpleName());
        return clazz.cast(stmt);
    }

    public static <T> T only(List<Stmt> stmts, Class<T> clazz) {
        assertEquals(1, stmts.size(), "Expected exactly one statement");
        return assertType(stmts.get(0), clazz);
    }

    public static <T> T at(List<Stmt> stmts, int index, Class<T> clazz) {
        assertTrue(index >= 0 && index < stmts.size(), "Index out of range");
        return assertType(stmts.get(index), clazz);
    }
}
