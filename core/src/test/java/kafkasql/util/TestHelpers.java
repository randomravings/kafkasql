package kafkasql.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.List;

import kafkasql.lang.Diagnostics;
import kafkasql.lang.KafkaSqlParser;
import kafkasql.lang.ParseArgs;
import kafkasql.lang.ParseResult;
import kafkasql.lang.DiagnosticEntry.Severity;
import kafkasql.lang.ast.Ast;
import kafkasql.lang.ast.Stmt;

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
        if (result.diags().hasError()) {
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

  /**
   * Prints diagnostic information for debugging test failures.
   * 
   * @param diags the Diagnostics object to print
   */
  public static void printDiagnostics(Diagnostics diags) {
    System.out.println("Diagnostics:");
    
    var sortedEntries = diags.get().stream()
        .sorted((a, b) -> Integer.compare(
            a.severity().ordinal(),
            b.severity().ordinal()))
        .toList();
    
    for (var entry : sortedEntries) {
      String prefix = switch (entry.severity()) {
        case FATAL -> "FATAL";
        case ERROR -> "ERROR";
        case WARNING -> "WARNING";
        case INFO -> "INFO";
      };
      System.out.println(prefix + ": " + entry.message());
    }
  }

  /**
   * Prints diagnostic information along with result details.
   * 
   * @param diags the Diagnostics object to print
   * @param result the result collection to print
   */
  public static void printDiagnosticsWithResult(Diagnostics diags, java.util.Collection<?> result) {
    printDiagnostics(diags);
    System.out.println("Result size: " + result.size());
    result.forEach(item -> System.out.println("  Item: " + item));
  }
}
