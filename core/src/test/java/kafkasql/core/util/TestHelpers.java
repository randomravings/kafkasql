package kafkasql.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;

import kafkasql.core.ParseArgs;
import kafkasql.core.KafkaSqlParser;
import kafkasql.core.ParseResult;
import kafkasql.core.ast.Ast;
import kafkasql.core.ast.Stmt;

public class TestHelpers {
    private TestHelpers() {
    }

    public static ParseResult parse(String script) {
        return KafkaSqlParser.parseText(script, new ParseArgs(Path.of(""), false, false));
    }

    public static Ast parseAssert(String script) {
        var result = KafkaSqlParser.parseText(script, new ParseArgs(Path.of(""), false, false));
        assertNoErrors(result);
        return result.ast();
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
